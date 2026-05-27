import { createContext, useState, useContext, useEffect } from 'react';

const LocationContext = createContext();

export function LocationProvider({ children }) {
  // ✅ 1. Read the initial location from localStorage, or use 'Hyderabad' as a fallback.
  const [location, setLocation] = useState(() => {
    return localStorage.getItem('userLocation') || 'Hyderabad';
  });

  // ✅ 2. Use useEffect to save the location to localStorage whenever it changes.
  useEffect(() => {
    localStorage.setItem('userLocation', location);
  }, [location]);

  return (
    <LocationContext.Provider value={{ location, setLocation }}>
      {children}
    </LocationContext.Provider>
  );
}

export function useLocation() {
  return useContext(LocationContext);
}
