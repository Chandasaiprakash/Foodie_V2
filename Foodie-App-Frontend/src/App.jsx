import React, { useContext, useState, useEffect } from "react";
import { BrowserRouter, Routes, Route, useLocation } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import axios from 'axios';

// Context Providers
import { AuthProvider, AuthContext } from "./context/AuthProvider";
import { CartProvider } from "./context/CartContext";
import { LocationProvider, useLocation as useLocationContext } from './context/LocationContext'; // Renamed to avoid conflict

// Toastify & Toaster
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { Toaster } from "react-hot-toast";

// Components & Pages
import Navbar from "./components/Navbar";
import Home from "./pages/Home";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Restaurants from "./pages/Restaurants";
import Menu from "./pages/Menu";
import Orders from "./pages/Orders";
import Cart from "./pages/Cart";
import Profile from "./pages/Profile";
import Payment from "./pages/Payment";
import Loader from "./components/Loader";
import SearchResultsPage from "./pages/SearchResultsPage";
import LocationPermissionModal from './components/LocationPermissionModal';

// Route Components
import PrivateRoute from "./components/PrivateRoute";
import PublicRoute from "./components/PublicRoute";

const OPENCAGE_API_KEY = '088ac5634d6a430993d7758f66740103'; // Your OpenCage API Key

// --- Animation Setup (No Changes Needed) ---
const pageVariants = {
  initial: { opacity: 0, x: -100 },
  in: { opacity: 1, x: 0 },
  out: { opacity: 0, x: 100 },
};
const pageTransition = {
  type: "tween",
  ease: "anticipate",
  duration: 0.5,
};
const AnimatedPage = ({ children }) => (
  <motion.div
    initial="initial"
    animate="in"
    exit="out"
    variants={pageVariants}
    transition={pageTransition}
    style={{ minHeight: "calc(100vh - 80px)" }}
  >
    {children}
  </motion.div>
);

// --- Routes Component (Simplified) ---
function AnimatedRoutes() {
  const location = useLocation();

  return (
    <AnimatePresence mode="wait">
      <Routes location={location} key={location.pathname}>
        {/* Public Pages */}
        <Route path="/" element={<AnimatedPage><Home /></AnimatedPage>} />
        <Route path="/search-results" element={<AnimatedPage><SearchResultsPage /></AnimatedPage>} />
        <Route path="/restaurants" element={<AnimatedPage><Restaurants /></AnimatedPage>} />
        <Route path="/restaurants/:restaurantId" element={<AnimatedPage><Menu /></AnimatedPage>} />
        <Route path="/login" element={<PublicRoute><AnimatedPage><Login /></AnimatedPage></PublicRoute>} />
        <Route path="/register" element={<PublicRoute><AnimatedPage><Register /></AnimatedPage></PublicRoute>} />

        {/* Protected Pages */}
        <Route path="/payment" element={<PrivateRoute><AnimatedPage><Payment /></AnimatedPage></PrivateRoute>} />
        <Route path="/cart" element={<PrivateRoute><AnimatedPage><Cart /></AnimatedPage></PrivateRoute>} />
        <Route path="/orders" element={<PrivateRoute><AnimatedPage><Orders /></AnimatedPage></PrivateRoute>} />
        <Route path="/profile" element={<PrivateRoute><AnimatedPage><Profile /></AnimatedPage></PrivateRoute>} />
      </Routes>
    </AnimatePresence>
  );
}

// --- Main App Content with Location Logic ---
function AppContent() {
  const { loading } = useContext(AuthContext);
  const { setLocation } = useLocationContext(); // Use our renamed context hook
  const [showLocationModal, setShowLocationModal] = useState(false);
  const [isDetecting, setIsDetecting] = useState(false);

  // This function fetches coordinates and sets the location
  const fetchAndSetLocation = () => {
    setIsDetecting(true);
    setShowLocationModal(false);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } = position.coords;
        axios
          .get(`https://api.opencagedata.com/geocode/v1/json?q=${latitude}+${longitude}&key=${OPENCAGE_API_KEY}`)
          .then((response) => {
            const city = response.data.results[0]?.components.city || response.data.results[0]?.components.state;
            if (city) {
              setLocation(city);
            }
          })
          .finally(() => setIsDetecting(false));
      },
      () => {
        setIsDetecting(false);
        console.error("User denied location access.");
      }
    );
  };

  // This useEffect runs only once on app startup to check permissions
  useEffect(() => {
    if (localStorage.getItem('userLocation')) {
      return; // Location already saved, do nothing
    }

    navigator.permissions?.query({ name: 'geolocation' }).then((result) => {
      if (result.state === 'granted') {
        fetchAndSetLocation();
      } else if (result.state === 'prompt') {
        setShowLocationModal(true);
      }
      // If 'denied', we do nothing to respect the user's choice
    });
  }, []); // Empty dependency array ensures this runs only once

  if (loading) {
    return <Loader />;
  }

  return (
    <>
      <Navbar />
      <AnimatedRoutes />
      <ToastContainer autoClose={3000} hideProgressBar position="bottom-right" />
      {showLocationModal && (
        <LocationPermissionModal 
          isDetecting={isDetecting}
          onAllow={fetchAndSetLocation} 
        />
      )}
    </>
  );
}

// --- Top-Level App Component with All Providers ---
export default function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <LocationProvider> {/* ✅ Single LocationProvider at the top */}
          <BrowserRouter>
            <AppContent />
            <Toaster position="top-right" toastOptions={{ /* ... your styles */ }} />
          </BrowserRouter>
        </LocationProvider>
      </CartProvider>
    </AuthProvider>
  );
}
