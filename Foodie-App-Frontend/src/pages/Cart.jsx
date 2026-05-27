// Cart.jsx
import { useContext } from "react";
import { CartContext } from "../context/CartContext";
import { AuthContext } from "../context/AuthProvider";
import { useNavigate } from "react-router-dom";
import { toast } from "react-toastify";

export default function Cart() {
  const { cart, addToCart, decreaseQty, removeFromCart } = useContext(CartContext);
  const { user } = useContext(AuthContext);
  const navigate = useNavigate();

  const total = cart.reduce((sum, item) => sum + item.price * item.qty, 0);

  const goToPayment = () => {
    if (!user) {
      toast.warn("⚠️ Please login first", { position: "bottom-right" });
      return;
    }

    navigate("/payment", {
  state: { total, fromCart: true },
});

  };

  return (
    <div className="container mx-auto py-10">
      <h1 className="text-2xl font-bold mb-6">Your Cart</h1>

      {cart.length === 0 ? (
        <p>Your cart is empty</p>
      ) : (
        <div className="bg-white shadow rounded-xl p-6">
          {cart.map((item, index) => (
            <div key={index} className="flex justify-between items-center border-b py-3">
              <div>
                <h2 className="font-semibold">{item.name}</h2>
                <p className="text-sm text-gray-600">₹ {item.price}</p>
                <p className="text-xs text-gray-400">Restaurant: {item.restaurantName}</p>
              </div>
              <div className="flex items-center space-x-2">
                <button
                  onClick={() => decreaseQty(item.name, item.restaurantId)}
                  className="px-3 py-1 bg-gray-200 rounded"
                >
                  -
                </button>
                <span>{item.qty}</span>
                <button
                  onClick={() =>
                    addToCart(item, {
                      restaurantId: item.restaurantId,
                      restaurantName: item.restaurantName,
                    })
                  }
                  className="px-3 py-1 bg-gray-200 rounded"
                >
                  +
                </button>
                <button
                  onClick={() => removeFromCart(item.name)}
                  className="px-3 py-1 bg-red-500 text-white rounded"
                >
                  Remove
                </button>
              </div>
            </div>
          ))}

          <div className="flex justify-between items-center mt-6">
            <h2 className="text-xl font-bold">Total: ₹ {total}</h2>
            <button
              onClick={goToPayment}
              className="bg-rose-600 text-white px-6 py-3 rounded-lg hover:bg-rose-700"
            >
              Proceed to Payment
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
