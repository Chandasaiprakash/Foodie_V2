import { motion, AnimatePresence } from "framer-motion";

export default function ConfirmPaymentModal({ show, onConfirm, onCancel, message }) {
  return (
    <AnimatePresence>
      {show && (
        <motion.div
          className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-50"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
        >
          <motion.div
            className="bg-white rounded-2xl shadow-lg p-6 max-w-sm w-full text-center"
            initial={{ scale: 0.8, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.8, opacity: 0 }}
            transition={{ duration: 0.25 }}
          >
            <h2 className="text-lg font-semibold mb-4 text-gray-800">{message}</h2>
            <div className="flex justify-center gap-4">
              <button
                onClick={onCancel}
                className="px-5 py-2 rounded-lg bg-gray-200 hover:bg-gray-300 transition-colors"
              >
                No
              </button>
              <button
                onClick={onConfirm}
                className="px-5 py-2 rounded-lg bg-rose-600 text-white hover:bg-rose-700 transition-colors"
              >
                Yes
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
