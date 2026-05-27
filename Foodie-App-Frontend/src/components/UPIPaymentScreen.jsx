import { motion } from "framer-motion";
import { useEffect, useState } from "react";

export default function UPIPaymentScreen({ total, onConfirm, onCancel }) {
  const [timer, setTimer] = useState(15);

  useEffect(() => {
    const countdown = setInterval(() => {
      setTimer((t) => (t > 0 ? t - 1 : 0));
    }, 1000);
    return () => clearInterval(countdown);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center h-[80vh] text-center space-y-6">
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ duration: 0.4 }}
        className="bg-white p-8 rounded-xl shadow-xl max-w-sm mx-auto"
      >
        <h2 className="text-xl font-bold text-gray-800 mb-4">
          Scan to Pay via UPI
        </h2>
        <p className="text-gray-600 mb-3">Total: ₹ {total}</p>

        <img
          src="https://api.qrserver.com/v1/create-qr-code/?data=upi://pay?pa=foodie@ybl&pn=FoodieApp&am=123&cu=INR&size=200x200"
          alt="QR Code"
          className="w-48 h-48 mx-auto rounded-lg border"
        />

        <p className="text-gray-500 mt-4 text-sm">
          Scan this QR code using any UPI app.
        </p>

        <p className="text-gray-400 mt-2 text-xs">
          Auto-confirming in {timer}s...
        </p>

        <div className="flex gap-3 mt-6">
          <button
            onClick={onCancel}
            className="flex-1 border border-gray-300 py-2 rounded-lg hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={onConfirm}
            disabled={timer === 0}
            className="flex-1 bg-rose-600 text-white py-2 rounded-lg hover:bg-rose-700"
          >
            I’ve Paid
          </button>
        </div>
      </motion.div>
    </div>
  );
}
