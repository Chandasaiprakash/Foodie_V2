import { useState, useEffect, useRef } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useLocation } from "../context/LocationContext";
import api from "../utils/api";

export default function SearchBar() {
  const [query, setQuery] = useState("");
  const [suggestions, setSuggestions] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const [showDropdown, setShowDropdown] = useState(false);
  const searchRef = useRef(null);
  const navigate = useNavigate();
  const { location } = useLocation();

  // 🛡️ Debouncing with proper cleanup
  useEffect(() => {
    if (!query.trim()) {
      console.log("❌ Empty query - clearing");
      setSuggestions([]);
      setShowDropdown(false);
      setIsLoading(false);
      return;
    }

    console.log("🎯 Starting search for:", query);
    setIsLoading(true);
    setShowDropdown(true);

    const searchTimer = setTimeout(async () => {
      try {
        console.log("🚀 Making API request...");
        const response = await api.get("/restaurants/search", {
          params: { query, address: location },
        });
        
        console.log("✅ Raw API response:", response);
        console.log("✅ Response data:", response.data);

        // 🎯 CRITICAL FIX: Handle the response data structure
        let results = [];
        
        if (Array.isArray(response.data)) {
          // If it's already an array
          results = response.data;
        } else if (response.data && typeof response.data === 'object') {
          // If it's an object, convert to array
          results = Object.values(response.data);
        }
        
        console.log("📊 Processed results:", results);
        
        // Filter out any invalid items and take first 5
        const validResults = results
          .filter(item => item && typeof item === 'object' && item.restaurantName)
          .slice(0, 5);
        
        console.log("🎉 Final valid results:", validResults);
        
        setSuggestions(validResults);
        setShowDropdown(true);
      } catch (error) {
        console.error("❌ Search failed:", error);
        console.error("Error response:", error.response?.data);
        setSuggestions([]);
      } finally {
        setIsLoading(false);
      }
    }, 400);

    return () => {
      console.log("🧹 Cleaning up previous search");
      clearTimeout(searchTimer);
    };
  }, [query, location]);

  // 🖱️ Close dropdown when clicking outside
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (searchRef.current && !searchRef.current.contains(event.target)) {
        console.log("👆 Click outside - closing dropdown");
        setShowDropdown(false);
      }
    };
    
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  // 🚀 Handle search submission
  const handleSearch = (e) => {
    e.preventDefault();
    if (query.trim()) {
      console.log("🔍 Executing search for:", query);
      navigate(`/search-results?q=${encodeURIComponent(query)}&address=${encodeURIComponent(location)}`);
      setShowDropdown(false);
      setQuery("");
    }
  };

  // Handle keyboard navigation
  const handleKeyDown = (e) => {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        setActiveIndex(prev => 
          prev < suggestions.length - 1 ? prev + 1 : 0
        );
        break;
      case "ArrowUp":
        e.preventDefault();
        setActiveIndex(prev => 
          prev > 0 ? prev - 1 : suggestions.length - 1
        );
        break;
      case "Enter":
        e.preventDefault();
        if (activeIndex >= 0 && suggestions[activeIndex]) {
          // Navigate to selected restaurant
          navigate(`/restaurants/${suggestions[activeIndex].restaurantId}`);
          setShowDropdown(false);
          setQuery("");
        } else {
          // Execute search
          handleSearch(e);
        }
        break;
      case "Escape":
        setShowDropdown(false);
        break;
    }
  };

  // Handle input focus
  const handleFocus = () => {
    if (query.trim() && (suggestions.length > 0 || isLoading)) {
      setShowDropdown(true);
    }
  };

  return (
    <div className="relative w-full max-w-3xl" ref={searchRef}>
      {/* Search Input */}
      <div className="relative">
        <input
          type="text"
          placeholder={`Search for restaurants and food in ${location}`}
          value={query}
          onChange={(e) => {
            const newQuery = e.target.value;
            console.log("⌨️ Input changed:", newQuery);
            setQuery(newQuery);
            setActiveIndex(-1);
            
            // Show dropdown when user starts typing
            if (newQuery.trim()) {
              setShowDropdown(true);
            }
          }}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          className="w-full pl-4 pr-10 py-2 border border-gray-300 rounded-lg shadow-sm focus:ring-2 focus:ring-rose-500 focus:border-rose-500 text-sm transition-all"
        />
        <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
          {isLoading ? (
            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-rose-600"></div>
          ) : (
            <svg className="h-5 w-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          )}
        </div>
      </div>

      {/* Dropdown Suggestions */}
      {showDropdown && (
        <div className="absolute z-40 w-full mt-2 bg-white border border-gray-200 rounded-lg shadow-xl max-h-80 overflow-y-auto">
          {/* Loading State */}
          {isLoading && (
            <div className="p-4 text-center text-gray-600">
              <div className="flex items-center justify-center gap-2">
                <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-rose-600"></div>
                <span>Searching for "{query}"...</span>
              </div>
            </div>
          )}

          {/* Results Found */}
          {!isLoading && suggestions.length > 0 && (
            <>
              {suggestions.map((restaurant, index) => (
                <Link
                  key={restaurant.restaurantId || index}
                  to={`/restaurants/${restaurant.restaurantId}`}
                  onClick={() => {
                    console.log("🎯 Selected restaurant:", restaurant.restaurantName);
                    setShowDropdown(false);
                    setQuery("");
                  }}
                  className={`flex items-center p-4 hover:bg-gray-50 border-b border-gray-100 transition-colors ${
                    index === activeIndex ? "bg-rose-50 border-rose-200" : ""
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <h3 className="font-semibold text-gray-900 truncate">
                      {restaurant.restaurantName}
                    </h3>
                    <p className="text-sm text-gray-600 mt-1">
                      {restaurant.cuisineType}
                    </p>
                    {restaurant.address && (
                      <p className="text-xs text-gray-500 mt-1 truncate">
                        {restaurant.address}
                      </p>
                    )}
                  </div>
                  <div className="flex items-center gap-1 ml-3 flex-shrink-0">
                    <span className="text-yellow-500">⭐</span>
                    <span className="text-sm font-medium text-gray-700">
                      {restaurant.rating || "New"}
                    </span>
                  </div>
                </Link>
              ))}
              
              {/* View All Results */}
              <div className="p-3 border-t border-gray-100 bg-gray-50">
                <button
                  onClick={handleSearch}
                  className="w-full text-center text-rose-600 hover:text-rose-700 font-semibold text-sm py-2 rounded-md hover:bg-rose-50 transition-colors"
                >
                  View all results for "{query}"
                </button>
              </div>
            </>
          )}

          {/* No Results */}
          {!isLoading && query.trim() && suggestions.length === 0 && (
            <div className="p-4 text-center text-gray-500">
              <p>No restaurants found for "{query}"</p>
              <p className="text-sm mt-1">Try different keywords</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
