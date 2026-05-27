import { useEffect, useState, useContext } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { CartContext } from "../context/CartContext";
import api from "../utils/api";

export default function Menu() {
  const { restaurantId } = useParams();
  const [restaurant, setRestaurant] = useState(null);
  const { cart, addToCart, decreaseQty } = useContext(CartContext);
  const navigate = useNavigate();

  useEffect(() => {
    if (!restaurantId) {
      console.error("❌ No restaurantId in params");
      return;
    }

    api
      .get(`/restaurants/${restaurantId}`)
      .then((res) => {
        console.log("📦 API response for restaurant:", res.data);
        setRestaurant(res.data);
      })
      .catch((err) => {
        console.error("❌ Error fetching restaurant:", err);
      });
  }, [restaurantId]);

  if (!restaurant) return <p className="text-center mt-10">Loading...</p>;

  return (
    <div className="container mx-auto py-10">
      <h1 className="text-3xl font-bold mb-4">{restaurant.restaurantName}</h1>
      <p className="text-gray-500">{restaurant.address}</p>

      <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
        {restaurant.menu.map((item, i) => {
          // ✅ check if item is in cart
          const cartItem = cart.find(
            (ci) =>
              ci.name === item.name && ci.restaurantId === restaurant.restaurantId
          );

          return (
            <div
              key={i}
              className="bg-white p-6 rounded-2xl shadow hover:shadow-md transition"
            >
              <h2 className="text-lg font-semibold">{item.name}</h2>
              <p className="text-gray-600">{item.description}</p>
              <p className="text-rose-600 font-bold">₹ {item.price}</p>

              {cartItem ? (
                <div className="mt-3 flex items-center space-x-3">
                  <button
                    onClick={() =>
                      decreaseQty(item.name, restaurant.restaurantId)
                    }
                    className="px-3 py-1 bg-gray-200 rounded"
                  >
                    -
                  </button>

                  <span>{cartItem.qty}</span>
                  <button
                    onClick={() => addToCart(item, restaurant)}
                    className="px-3 py-1 bg-gray-200 rounded"
                  >
                    +
                  </button>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => addToCart(item, restaurant)}
                  className="mt-3 bg-rose-600 text-white px-4 py-2 rounded-lg hover:bg-rose-700 cursor-pointer"
                >
                  Add to Cart
                </button>
              )}
            </div>
          );
        })}
      </div>

      {/* ✅ Floating Go to Cart Button */}
      {cart.length > 0 && (
        <button
          onClick={() => navigate("/cart")}
          className="fixed bottom-6 right-6 bg-rose-600 hover:bg-rose-700 text-white px-6 py-3 rounded-full shadow-lg font-semibold transition flex items-center gap-2"
        >
          🛒 Go to Cart ({cart.reduce((sum, item) => sum + item.qty, 0)})
        </button>
      )}
      
    </div>
  );
}
