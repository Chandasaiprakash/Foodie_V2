import { createContext, useState, useEffect } from "react";
import api from "../utils/api";

export const AuthContext = createContext();

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true); // optional: track initial auth check

  // Set token in API headers
  const setAuthToken = (token) => {
    if (token) {
      api.defaults.headers.common["Authorization"] = `Bearer ${token}`;
    } else {
      delete api.defaults.headers.common["Authorization"];
    }
  };

  useEffect(() => {
    const token = localStorage.getItem("token");
    if (token) {
      setAuthToken(token);
      api.get("/auth/me")
        .then(res => setUser(res.data))
        .catch(() => {
          localStorage.removeItem("token");
          setUser(null);
        })
        .finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, []);

  const login = async (email, password) => {
  const res = await api.post("/auth/login", { email, password });
  const { token,id, email: userEmail,phoneNumber, role,username } = res.data;

  localStorage.setItem("token", token);
  api.defaults.headers.common["Authorization"] = `Bearer ${token}`;
  setUser({ email: userEmail,id,phoneNumber, role, username  });
  
};

console.log("userfromAUTHPROVIDER", user);
//console.log("phoneNumber", user.phoneNumber);
  
  const register = async (userData) => {
  const res = await api.post("/auth/register", userData);
  const { token, email: userEmail,id,phoneNumber, role, username } = res.data;

  localStorage.setItem("token", token);
  setAuthToken(token);
  setUser({ email: userEmail,id,phoneNumber, role, token, username  });
};


  const logout = () => {
    localStorage.removeItem("token");
    setAuthToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
