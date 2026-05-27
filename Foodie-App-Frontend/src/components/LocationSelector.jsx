import { useState } from 'react';
import { useLocation } from '../context/LocationContext';
import LocationModal from './LocationModal'; // Import the new modal
import { AnimatePresence } from 'framer-motion';

export default function LocationSelector() {
  const { location } = useLocation();
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <>
      <div
        className="flex items-center p-3 cursor-pointer group"
        onClick={() => setIsModalOpen(true)}
      >
        <span className="font-semibold text-gray-800 border-b-2 border-dotted border-gray-800 group-hover:border-rose-500 group-hover:text-rose-500 whitespace-nowrap">
          {location || 'Select Location'}
        </span>
        
      </div>

      <AnimatePresence>
        {isModalOpen && <LocationModal onClose={() => setIsModalOpen(false)} />}
      </AnimatePresence>
    </>
  );
}