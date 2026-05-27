import { useState } from 'react';
import { useLocation } from '../context/LocationContext';
import { motion } from 'framer-motion';
import axios from 'axios';

const OPENCAGE_API_KEY = '088ac5634d6a430993d7758f66740103';

export default function LocationModal({ onClose }) {
  const { setLocation } = useLocation();
  const [inputValue, setInputValue] = useState('');
  const [isDetecting, setIsDetecting] = useState(false);

  const handleLocationSubmit = (e) => {
    e.preventDefault();
    if (inputValue.trim()) {
      setLocation(inputValue.trim());
      onClose();
    }
  };

  const handleDetectLocation = () => {
    setIsDetecting(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        axios
          .get(`https://api.opencagedata.com/geocode/v1/json?q=${latitude}+${longitude}&key=${OPENCAGE_API_KEY}`)
          .then((response) => {
            const city = response.data.results[0]?.components.city || response.data.results[0]?.components.state;
            setLocation(city || 'Unknown');
            setIsDetecting(false);
            onClose();
          });
      },
      () => {
        setIsDetecting(false);
        alert('Could not detect location. Please enable location services.');
      }
    );
  };

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 bg-black bg-opacity-50 z-50 flex justify-center items-start pt-20"
      onClick={onClose}
    >
      <motion.div
        initial={{ y: -50, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        exit={{ y: -50, opacity: 0 }}
        className="bg-white rounded-lg shadow-xl w-full max-w-md p-6"
        onClick={(e) => e.stopPropagation()} // Prevent closing when clicking inside
      >
        <div className="flex justify-between items-center mb-4">
          <h2 className="text-xl font-bold">Select Location</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-800">&times;</button>
        </div>

        <form onSubmit={handleLocationSubmit} className="relative mb-4">
          <input
            type="text"
            placeholder="Enter your city..."
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            className="w-full p-3 border rounded-lg focus:ring-2 focus:ring-rose-500"
          />
        </form>

        <button
          onClick={handleDetectLocation}
          className="w-full p-3 border border-rose-500 text-rose-500 rounded-lg font-semibold flex items-center justify-center gap-2 hover:bg-rose-50 transition-colors"
          disabled={isDetecting}
        >
          {isDetecting ? (
            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-rose-500"></div>
          ) : (
            '📍 Detect My Location'
          )}
        </button>
      </motion.div>
    </motion.div>
  );
}