import { useState } from "react";
import { motion } from "framer-motion";

export default function CardPaymentScreen({ total, onConfirm, onCancel }) {
  const [cardNumber, setCardNumber] = useState("");
  const [expiry, setExpiry] = useState("");
  const [cvv, setCvv] = useState("");
  const [processing, setProcessing] = useState(false);

  const handleConfirm = async () => {
    if (cardNumber.length < 16 || expiry.length < 5 || cvv.length < 3) {
      alert("Please enter valid card details.");
      return;
    }

    setProcessing(true);
    await new Promise((res) => setTimeout(res, 2000)); // simulate delay
    onConfirm();
  };

  return (
    <div className="flex flex-col items-center justify-center h-[80vh] text-center space-y-6">
      <motion.div
        initial={{ scale: 0.9, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.3 }}
        className="bg-white p-8 rounded-xl shadow-xl max-w-sm mx-auto"
      >
        <h2 className="text-xl font-bold text-gray-800 mb-4">
          Pay ₹{total} via Card
        </h2>

        <div className="space-y-4 text-left">
          <div>
            <label className="text-sm text-gray-600">Card Number</label>
            <input
              type="text"
              maxLength="16"
              value={cardNumber}
              onChange={(e) => setCardNumber(e.target.value.replace(/\D/g, ""))}
              className="w-full border rounded-lg p-2 mt-1"
              placeholder="1234 5678 9012 3456"
              disabled={processing}
            />
          </div>

          <div className="flex space-x-3">
            <div className="flex-1">
              <label className="text-sm text-gray-600">Expiry</label>
              <input
                type="text"
                maxLength="5"
                value={expiry}
                onChange={(e) =>
                  setExpiry(e.target.value.replace(/[^0-9/]/g, ""))
                }
                className="w-full border rounded-lg p-2 mt-1"
                placeholder="MM/YY"
                disabled={processing}
              />
            </div>
            <div className="w-20">
              <label className="text-sm text-gray-600">CVV</label>
              <input
                type="password"
                maxLength="3"
                value={cvv}
                onChange={(e) => setCvv(e.target.value.replace(/\D/g, ""))}
                className="w-full border rounded-lg p-2 mt-1"
                placeholder="123"
                disabled={processing}
              />
            </div>
          </div>
        </div>

        <div className="flex gap-3 mt-6">
          <button
            onClick={onCancel}
            disabled={processing}
            className="flex-1 border border-gray-300 py-2 rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleConfirm}
            disabled={processing}
            className={`flex-1 py-2 rounded-lg text-white font-medium transition-colors ${
              processing
                ? "bg-gray-400 cursor-not-allowed"
                : "bg-rose-600 hover:bg-rose-700"
            }`}
          >
            {processing ? "Processing..." : "Pay ₹" + total}
          </button>
        </div>
      </motion.div>
    </div>
  );
}
