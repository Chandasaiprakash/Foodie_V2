import { useState, useEffect, useRef, useContext } from "react";
import { Link } from "react-router-dom";
import { AuthContext } from "../context/AuthProvider";

// We pass the navigation handler functions from the Navbar as props
export default function KebabMenu({ handleProtectedNav, handleLogout }) {
  const [isOpen, setIsOpen] = useState(false);
  const { user } = useContext(AuthContext);
  const menuRef = useRef(null);

  // Close the menu if the user clicks outside of it
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (menuRef.current && !menuRef.current.contains(event.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <div className="relative" ref={menuRef}>
      <button onClick={() => setIsOpen(!isOpen)} className="p-2 rounded-full hover:bg-gray-100 transition-colors">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6 text-gray-700">
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 6.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 12.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5ZM12 18.75a.75.75 0 1 1 0-1.5.75.75 0 0 1 0 1.5Z" />
        </svg>
      </button>

      {isOpen && (
        <div className="absolute right-0 mt-2 w-56 bg-white border rounded-lg shadow-xl z-20">
          <div className="py-1">
            {user ? (
              <>
                <Link to="/profile" onClick={(e) => { handleProtectedNav(e, "/profile"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  {user.username || user.email}
                </Link>
                <Link to="/orders" onClick={(e) => { handleProtectedNav(e, "/orders"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Orders
                </Link>
                <Link to="/cart" onClick={(e) => { handleProtectedNav(e, "/cart"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Cart
                </Link>
                <Link to="/restaurants" onClick={(e) => { handleProtectedNav(e, "/restaurants"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Restaurants
                </Link>
                <div className="border-t my-1"></div>
                <button onClick={(e) => { handleLogout(e); setIsOpen(false); }} className="block w-full text-left px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Logout
                </button>
              </>
            ) : (
              <>
                <Link to="/login" onClick={(e) => { handleProtectedNav(e, "/login"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Login
                </Link>
                <Link to="/register" onClick={(e) => { handleProtectedNav(e, "/register"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Register
                </Link>
                <Link to="/cart" onClick={(e) => { handleProtectedNav(e, "/cart"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Cart
                </Link>
                 <Link to="/restaurants" onClick={(e) => { handleProtectedNav(e, "/restaurants"); setIsOpen(false); }} className="block px-4 py-2 text-gray-800 hover:bg-gray-100">
                  Restaurants
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
