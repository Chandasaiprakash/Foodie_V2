import { Link, useLocation, useNavigate } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { useState, useContext, useRef, useEffect } from "react";
import { AuthContext } from "../context/AuthProvider";
import ConfirmPaymentModal from "../components/ConfirmPaymentModal";
import SearchBar from "./SearchBar";
import LocationSelector from "./LocationSelector";
import KebabMenu from "./KebabMenu";
import Logo from "./Logo";

export default function Navbar() {
  const { user, logout } = useContext(AuthContext);
  const location = useLocation();
  const navigate = useNavigate();

  const [isSearchActive, setIsSearchActive] = useState(false);
  const navbarRef = useRef(null);

  // All your existing modal state and logic
  const [modalVisible, setModalVisible] = useState(false);
  const [pendingAction, setPendingAction] = useState(null);
  const [message, setMessage] = useState("");

  const handleProtectedNav = (e, path) => {
    if (location.pathname.startsWith("/payment")) {
      e.preventDefault();
      setMessage("Cancel payment and leave this page?");
      setPendingAction(() => () => navigate(path));
      setModalVisible(true);
    }
  };

  const handleLogout = (e) => {
    if (location.pathname.startsWith("/payment")) {
      e.preventDefault();
      setMessage("Cancel payment and logout?");
      setPendingAction(() => logout);
      setModalVisible(true);
    } else {
      logout();
    }
  };

  // Close search when clicking outside the navbar
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (navbarRef.current && !navbarRef.current.contains(event.target)) {
        setIsSearchActive(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <>
      <motion.nav
        initial={{ y: -50, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.6 }}
        className="bg-white shadow-md sticky top-0 z-40"
        ref={navbarRef}
      >
        <div className="container mx-auto flex justify-between items-center p-4">
          {/* --- LEFT GROUP --- */}
          <div className="flex items-center">
            <Logo />
          </div>

          {/* --- CENTER GROUP (conditionally rendered) --- */}
          <div className="flex-grow flex justify-center px-4">
            <AnimatePresence>
              {isSearchActive ? (
                <motion.div
                  key="search-active"
                  initial={{ width: "0%", opacity: 0 }}
                  animate={{ width: "100%", opacity: 1 }}
                  exit={{ width: "0%", opacity: 0 }}
                  transition={{ duration: 0.3 }}
                  className="w-full max-w-2xl"
                >
                  <div className="w-full flex items-center rounded-lg shadow-sm">
                    <LocationSelector />
                    <div className="w-px h-6 bg-gray-200" />
                    <div className="flex-grow">
                      <SearchBar />
                    </div>
                  </div>
                </motion.div>
              ) : (
                <motion.div key="search-inactive" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
                  {/* This is intentionally empty in the normal view to create space */}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          {/* --- RIGHT GROUP --- */}
          <div className="flex items-center space-x-6">
            <AnimatePresence mode="wait">
              {!isSearchActive ? (
                <motion.div
                  key="nav-links"
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  exit={{ opacity: 0 }}
                  className="hidden md:flex items-center space-x-6"
                >
                   <button
                    className="flex items-center space-x-2 cursor-pointer p-2 rounded-lg hover:bg-gray-100"
                    onClick={() => setIsSearchActive(true)}
                  >
                    <svg className="w-5 h-5 text-gray-700"  xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" d="m21 21-5.197-5.197m0 0A7.5 7.5 0 1 0 5.196 5.196a7.5 7.5 0 0 0 10.607 10.607Z" />
                    </svg>
                    <span className="font-medium text-gray-700">Search</span>
                  </button>
                  <Link to="/restaurants" onClick={(e) => handleProtectedNav(e, "/restaurants")} className="hover:text-rose-600 transition-colors">
                    Restaurants
                  </Link>
                  <Link to="/cart" onClick={(e) => handleProtectedNav(e, "/cart")} className="hover:text-rose-600 transition-colors">
                    Cart
                  </Link>
                  {user ? (
                    <>
                      <Link to="/orders" onClick={(e) => handleProtectedNav(e, "/orders")} className="hover:text-rose-600 transition-colors">
                        Orders
                      </Link>
                      <Link to="/profile" className="font-medium text-gray-700 hover:text-rose-600">{user.username || user.email}</Link>
                      <button onClick={handleLogout} className="bg-gray-800 text-white px-4 py-2 rounded-lg hover:bg-gray-900 transition-colors">
                        Logout
                      </button>
                    </>
                  ) : (
                    <Link to="/login" onClick={(e) => handleProtectedNav(e, "/login")} className="bg-rose-600 text-white px-4 py-2 rounded-lg hover:bg-rose-700 transition-colors">
                      Login
                    </Link>
                  )}
                </motion.div>
              ) : (
                <motion.div
                  key="kebab-menu"
                  initial={{ opacity: 0, scale: 0.8 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 0.8 }}
                >
                  <KebabMenu handleProtectedNav={handleProtectedNav} handleLogout={handleLogout} />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </motion.nav>

      <ConfirmPaymentModal
        show={modalVisible}
        message={message}
        onConfirm={() => {
          pendingAction?.();
          setModalVisible(false);
        }}
        onCancel={() => setModalVisible(false)}
      />
    </>
  );
}

