import { useContext, useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { AuthContext } from "../context/AuthProvider";
import { motion } from "framer-motion";

const PrivateRoute = ({ children }) => {
  const { user, loading } = useContext(AuthContext);
  const [redirect, setRedirect] = useState(false);

  useEffect(() => {
    if (!loading && !user) {
      // Delay redirect for fade-out
      const timer = setTimeout(() => setRedirect(true), 300);
      return () => clearTimeout(timer);
    }
  }, [user, loading]);

  if (loading) return <div>Loading...</div>;

  if (redirect) return <Navigate to="/login" replace />;

  if (!user) {
    // Fade-out unauthorized content before redirect
    return (
      <motion.div
        initial={{ opacity: 1 }}
        animate={{ opacity: 0 }}
        transition={{ duration: 0.3 }}
      >
        {children}
      </motion.div>
    );
  }

  // Authorized → fade-in protected page
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.3 }}
    >
      {children}
    </motion.div>
  );
};

export default PrivateRoute;
