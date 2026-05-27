import { motion } from 'framer-motion';
import { useState } from 'react';

export default function LocationPermissionModal({ onAllow, isDetecting }) {
  return (
    <div className="fixed inset-0 bg-black bg-opacity-60 z-50 flex items-center justify-center p-4">
      <motion.div
        initial={{ y: 50, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        className="bg-white rounded-2xl shadow-xl w-full max-w-sm text-center p-6"
      >
        <div className="text-5xl mb-4">📍</div>
        <h2 className="text-2xl font-bold text-gray-800 mb-2">Enable Location Services</h2>
        <p className="text-gray-600 mb-6">
          To show you the best restaurants and deals in your area, we need to know your location.
        </p>

        <button
          onClick={onAllow}
          disabled={isDetecting}
          className="w-full bg-rose-600 text-white font-bold py-3 px-4 rounded-lg hover:bg-rose-700 transition-colors flex items-center justify-center gap-2 disabled:opacity-50"
        >
          {isDetecting ? (
            <>
              <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
              <span>Detecting...</span>
            </>
          ) : (
            'Allow Location Access'
          )}
        </button>
        <p className="text-xs text-gray-400 mt-3">You can change this in your browser settings later.</p>
      </motion.div>
    </div>
  );
}