import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useLocation } from "../context/LocationContext";
import api from "../utils/api";

export default function Restaurants() {
  const [restaurants, setRestaurants] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const { location } = useLocation();

  useEffect(() => {
    // Don't fetch if location is not available yet
    if (!location) {
      setIsLoading(false);
      setError("Location not available");
      return;
    }

    setIsLoading(true);
    setError(null);

    api.get("/restaurants", { params: { location } })
      .then(res => {
        setRestaurants(res.data);
        setIsLoading(false);
      })
      .catch(err => {
        console.error(err);
        setError("Failed to fetch restaurants");
        setIsLoading(false);
      });
  }, [location]); // ✅ Added location dependency

  console.log(restaurants);

  // Loading state
  if (isLoading) {
    return (
      <div className="container mx-auto py-10 text-center">
        <div className="text-lg">Loading restaurants...</div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="container mx-auto py-10 text-center">
        <div className="text-red-500 text-lg">{error}</div>
        <p className="text-gray-500">Please ensure your location is set correctly.</p>
      </div>
    );
  }

  // No restaurants found
  if (restaurants.length === 0) {
    return (
      <div className="container mx-auto py-10 text-center">
        <div className="text-lg text-gray-500">No restaurants found in {location}.</div>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-10 grid grid-cols-1 md:grid-cols-3 gap-6">
      {restaurants.map(r => (
        <Link 
          to={`/restaurants/${r.restaurantId}`} 
          key={r.restaurantId} 
          className="bg-white p-6 rounded-2xl shadow hover:shadow-lg transition"
        >
          <h2 className="text-xl font-semibold">{r.restaurantName}</h2>
          <p className="text-gray-500">{r.cuisineType}</p>
          <p className="text-yellow-500">⭐ {r.rating}</p>
        </Link>
      ))}
    </div>
  );
}
