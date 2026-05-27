import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";

export default function PaymentFailed({ reason }) {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center h-[70vh] text-center space-y-6">
      <motion.div
        initial={{ scale: 0 }}
        animate={{ scale: 1 }}
        transition={{ duration: 0.5 }}
        className="w-24 h-24 flex items-center justify-center bg-red-100 rounded-full"
      >
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="w-12 h-12 text-red-600"
          fill="none"
          viewBox="0 0 24 24"
          strokeWidth={2}
          stroke="currentColor"
        >
          <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </motion.div>

      <h2 className="text-2xl font-bold text-red-600">Payment Failed</h2>
      <p className="text-gray-600">
        {reason || "Something went wrong. Your payment was not completed."}
      </p>

      <button
        onClick={() => navigate("/cart")}
        className="mt-4 bg-rose-600 text-white px-6 py-3 rounded-lg hover:bg-rose-700 transition"
      >
        Try Again
      </button>
    </div>
  );
}
