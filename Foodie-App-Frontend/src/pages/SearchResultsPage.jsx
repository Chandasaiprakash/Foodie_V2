// src/pages/SearchResultsPage.jsx
import { useState, useEffect } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { useLocation } from '../context/LocationContext';
import api from "../utils/api";

export default function SearchResultsPage() {
  const [searchResults, setSearchResults] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchParams] = useSearchParams();
  const query = searchParams.get("q");
  const { location } = useLocation();
  const address = searchParams.get("address");

  useEffect(() => {
    if (!query|| !location) {
      setIsLoading(false);
      return;
    }
    setIsLoading(true);
    api
      .get("/restaurants/search", {
        params: { query, ...(address ? { address } : {}) },
      })
      .then((res) => {
        setSearchResults(res.data);
        setIsLoading(false);
      })
      .catch((err) => {
        console.error("Full search error:", err);
        setIsLoading(false);
      });
  }, [query, address]);

  if (isLoading) {
    return <div className="text-center p-10">Loading results...</div>;
  }

  return (
    <div className="container mx-auto p-4">
      <h1 className="text-3xl font-bold mb-4">Search Results for "{query}" </h1>
      {searchResults.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {searchResults.map((r) => (
            <Link to={`/restaurants/${r.restaurantId}`} key={r.restaurantId} className="border rounded-lg overflow-hidden shadow-sm hover:shadow-lg transition-shadow">
              <div className="p-4">
                <h2 className="text-xl font-semibold">{r.restaurantName}</h2>
                <p className="text-gray-600">{r.cuisineType}</p>
                <div className="mt-2 text-yellow-500">⭐ {r.rating}</div>
              </div>
            </Link>
          ))}
        </div>
      ) : (
        <p>No results found for your search.</p>
      )}
    </div>
  );
}
