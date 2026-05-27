import { useLocation, useNavigate } from "react-router-dom";
import { useContext, useEffect, useState } from "react";
import { CartContext } from "../context/CartContext";
import { AuthContext } from "../context/AuthProvider";
import api from "../utils/api";
import { toast } from "react-toastify";
import ConfirmPaymentModal from "../components/ConfirmPaymentModal";
import { motion } from "framer-motion";
import OrderSuccess from "../components/OrderSuccess";
import UPIPaymentScreen from "../components/UPIPaymentScreen";
import CardPaymentScreen from "../components/CardPaymentScreen";
import PaymentFailed from "../components/PaymentFailed";

export default function Payment() {
  const navigate = useNavigate();
  const { user } = useContext(AuthContext);
  const { cart, clearCart } = useContext(CartContext);
  const location = useLocation();

  const [processing, setProcessing] = useState(false);
  const [progress, setProgress] = useState(0);
  const [isPaymentComplete, setIsPaymentComplete] = useState(false);
  const [orderUuid, setOrderUuid] = useState(null);
  const [showModal, setShowModal] = useState(false);
  const [pendingAction, setPendingAction] = useState(null);
  const [modalMessage, setModalMessage] = useState("");
  const [showProcessingScreen, setShowProcessingScreen] = useState(false);
  const [processingMessage, setProcessingMessage] = useState("");
  const [selectedMethod, setSelectedMethod] = useState(null);
  const [showUPIScreen, setShowUPIScreen] = useState(false);
  const [showCardScreen, setShowCardScreen] = useState(false);
  const [isPaymentFailed, setIsPaymentFailed] = useState(false);
  const [failureReason, setFailureReason] = useState("");

  const total =
    location.state?.total || cart.reduce((sum, i) => sum + i.price * i.qty, 0);

  // 🚫 Block direct access to /payment
  useEffect(() => {
    if (isPaymentComplete) return;
    if (!location.state?.fromCart || cart.length === 0) {
      navigate("/orders", { replace: true });
    }
  }, [location.state, cart, navigate]);

  // 🧠 Warn before closing tab
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (!isPaymentComplete && !processing) {
        e.preventDefault();
        e.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [isPaymentComplete, processing]);

  // ⬅️ Trap Back button
  useEffect(() => {
    const handlePopState = () => {
      if (!isPaymentComplete && !processing) {
        setModalMessage("Cancel payment and go back?");
        setPendingAction(() => () => navigate("/cart", { replace: true }));
        setShowModal(true);
        window.history.pushState(null, "", window.location.href);
      }
    };

    window.history.pushState(null, "", window.location.href);
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, [isPaymentComplete, processing, navigate]);

  // 🌀 Progress bar animation
  useEffect(() => {
    if (processing) {
      setProgress(0);
      const interval = setInterval(() => {
        setProgress((prev) => (prev >= 90 ? prev : prev + 5));
      }, 200);
      return () => clearInterval(interval);
    } else {
      setProgress(0);
    }
  }, [processing]);

  // 🔌 Load Razorpay dynamically
  const loadRazorpay = () => {
    return new Promise((resolve) => {
      if (window.Razorpay) {
        resolve(true);
        return;
      }
      const script = document.createElement("script");
      script.src = "https://checkout.razorpay.com/v1/checkout.js";
      script.onload = () => resolve(true);
      script.onerror = () => resolve(false);
      document.body.appendChild(script);
    });
  };

  // 💳 Handle Payment
  const handlePayFinal = async () => {
    if (!selectedMethod) {
      toast.warn("Please select a payment method.", { position: "bottom-right" });
      return;
    }

    setShowProcessingScreen(true);
    setProcessingMessage(`Processing your ${selectedMethod.toUpperCase()} payment...`);
    setProcessing(true);

    try {
      // Step 1: Create order
      const orderItems = cart.map((item) => ({
        name: item.name,
        price: item.price,
        quantity: item.qty,
        restaurantId: item.restaurantId,
        restaurantName: item.restaurantName,
      }));

      const orderRes = await api.post("/orders", {
        userId: user.id,
        customerEmail: user.email,
        items: orderItems,
        total,
        paymentMethod: selectedMethod,
        restaurantId: cart[0]?.restaurantId,
        restaurantName: cart[0]?.restaurantName,
      });

      const createdOrder = orderRes.data;
      setOrderUuid(createdOrder.orderUuid);

      // Step 2: Request Razorpay order from backend
      const { data: razorData } = await api.post("/payments/create", {
        orderUuid: createdOrder.orderUuid,
        amount: total,
        customerEmail: user.email,
      });

      // Step 3: Load Razorpay SDK
      const isLoaded = await loadRazorpay();
      if (!isLoaded) {
        toast.error("Failed to load Razorpay SDK. Please try again later.");
        setShowProcessingScreen(false);
        setProcessing(false);
        return;
      }

      // Step 4: Open Razorpay popup
      const options = {
        key: razorData.razorKey,
        amount: razorData.amount,
        currency: razorData.currency,
        name: "Foodie",
        description: "Order Payment",
        order_id: razorData.orderId,
        handler: async function (response) {
          try {
            await api.post("/payments/verify", {
              orderUuid: createdOrder.orderUuid,
              razorpay_order_id: response.razorpay_order_id,
              razorpay_payment_id: response.razorpay_payment_id,
              razorpay_signature: response.razorpay_signature,
            });
            clearCart();
            toast.success("✅ Payment successful!", { position: "bottom-right" });
            setProgress(100);
            setIsPaymentComplete(true);
            setShowProcessingScreen(false);
          } catch {
            await api.post("/payments/fail", {
              orderUuid: createdOrder.orderUuid,
              reason: "Payment verification failed",
            });
            setIsPaymentFailed(true);
            setFailureReason("Payment verification failed");
            toast.error("Payment verification failed.", { position: "bottom-right" });
            setShowProcessingScreen(false);
          }
        },
        modal: {
          ondismiss: async function () {
            await api.post("/payments/fail", {
              orderUuid: createdOrder.orderUuid,
              reason: "Payment cancelled by user",
            });
            setProcessing(false);
            setShowProcessingScreen(false);
            setProgress(0);
            setIsPaymentFailed(true);
            setFailureReason("Payment cancelled by user");
            toast.info("Payment cancelled", { position: "bottom-right" });
          },
        },
        prefill: {
          name: user.name,
          email: user.email,
        },
        theme: { color: "#F37254" },
      };

      const rzp = new window.Razorpay(options);
      rzp.open();
    } catch (err) {
      await api.post("/payments/fail", {
        orderUuid: orderUuid,
        reason: err.message || "Payment failed",
      });
      console.error("❌ Payment error:", err);
      toast.error("❌ Payment failed. Please try again.", { position: "bottom-right" });
      setShowProcessingScreen(false);
    } finally {
      setProcessing(false);
    }
  };

  const handleCancelClick = () => {
    setModalMessage("Cancel payment?");
    setPendingAction(() => () => navigate("/cart", { replace: true }));
    setShowModal(true);
  };

  // ✅ Success / Failure Screens
  if (isPaymentComplete) {
    return <OrderSuccess orderUuid={orderUuid} paymentMethod={selectedMethod} />;
  }

  if (isPaymentFailed) {
    return <PaymentFailed reason={failureReason} />;
  }

  if (showProcessingScreen) {
    return (
      <div className="flex flex-col items-center justify-center h-[70vh] text-center">
        <motion.div
          animate={{ rotate: 360 }}
          transition={{ repeat: Infinity, duration: 1, ease: "linear" }}
          className="w-16 h-16 border-4 border-rose-500 border-t-transparent rounded-full mb-6"
        />
        <h2 className="text-xl font-semibold text-gray-800">{processingMessage}</h2>
        <p className="text-gray-500 mt-2">Please don’t refresh or close the tab...</p>
      </div>
    );
  }

  // 💰 Payment Page
  return (
    <div className="container mx-auto py-10">
      {/* Progress Bar */}
      <div className="fixed top-0 left-0 w-full h-1 bg-gray-200 z-50">
        <motion.div
          className={`h-1 ${isPaymentComplete ? "bg-green-500" : "bg-rose-600"}`}
          initial={{ width: 0 }}
          animate={{ width: `${progress}%` }}
          transition={{ ease: "easeInOut", duration: 0.3 }}
        />
      </div>

      <h1 className="text-2xl font-bold mb-6">Payment</h1>

      <div className="bg-white shadow rounded-xl p-6 max-w-md mx-auto space-y-6">
        <p>
          <strong>Total Amount:</strong> ₹ {total}
        </p>

        <p><strong>Select Payment Method:</strong></p>
        <div className="space-y-3">
          {["upi", "card"].map((method) => (
            <label
              key={method}
              className={`flex items-center justify-between border rounded-lg p-3 cursor-pointer ${
                selectedMethod === method ? "border-rose-600 bg-rose-50" : "border-gray-300"
              }`}
              onClick={() => setSelectedMethod(method)}
            >
              <span className="capitalize">
                {method === "upi" ? "UPI / QR" : "Credit / Debit Card"}
              </span>
              {selectedMethod === method && (
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth={2}
                  stroke="currentColor"
                  className="w-5 h-5 text-rose-600"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              )}
            </label>
          ))}
        </div>

        <button
          onClick={handlePayFinal}
          disabled={processing}
          className={`w-full py-3 rounded-lg text-white font-medium transition-colors ${
            processing ? "bg-gray-400 cursor-not-allowed" : "bg-rose-600 hover:bg-rose-700"
          }`}
        >
          {processing ? "Processing Payment..." : "Pay Now"}
        </button>

        <button
          onClick={handleCancelClick}
          disabled={processing}
          className={`w-full mt-2 py-3 rounded-lg border font-medium transition ${
            processing
              ? "border-gray-300 text-gray-400 cursor-not-allowed"
              : "border-rose-600 text-rose-600 hover:bg-rose-50"
          }`}
        >
          Cancel
        </button>
      </div>

      <ConfirmPaymentModal
        show={showModal}
        message={modalMessage}
        onConfirm={() => {
          setShowModal(false);
          pendingAction?.();
        }}
        onCancel={() => setShowModal(false)}
      />
    </div>
  );
}
