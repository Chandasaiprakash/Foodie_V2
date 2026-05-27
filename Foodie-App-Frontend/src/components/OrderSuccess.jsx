// OrderSuccess.jsx
import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";

export default function OrderSuccess({ orderUuid }) {
  const navigate = useNavigate();

  return (
    <div className="container mx-auto py-10">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5 }}
        className="max-w-md mx-auto text-center"
      >
        <div className="bg-white shadow-lg rounded-xl p-8 space-y-6">
          <div className="w-16 h-16 bg-green-100 rounded-full flex items-center justify-center mx-auto">
            <svg className="w-8 h-8 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>
          
          <h2 className="text-2xl font-bold text-gray-800">Payment Successful!</h2>
          
          <p className="text-gray-600">
            Your order has been placed successfully.
          </p>
          
          {orderUuid && (
            <p className="text-sm text-gray-500">
              Order ID: <span className="font-mono">{orderUuid}</span>
            </p>
          )}

          <div className="space-y-3 pt-4">
            <button
              onClick={() => navigate("/orders")}
              className="w-full bg-rose-600 text-white py-3 rounded-lg font-medium hover:bg-rose-700 transition-colors"
            >
              View Orders
            </button>
            
            <button
              onClick={() => navigate("/")}
              className="w-full border border-gray-300 text-gray-700 py-3 rounded-lg font-medium hover:bg-gray-50 transition-colors"
            >
              Continue Shopping
            </button>
          </div>
        </div>
      </motion.div>
    </div>
  );
}