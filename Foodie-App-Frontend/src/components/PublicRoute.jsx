import { useContext } from "react";
import { Navigate } from "react-router-dom";
import { AuthContext } from "../context/AuthProvider";

const PublicRoute = ({ children }) => {
  const { user, loading } = useContext(AuthContext);

  if (loading) return <div>Loading...</div>; // wait until auth check completes

  return user ? <Navigate to="/" replace /> : children;
};

export default PublicRoute;
