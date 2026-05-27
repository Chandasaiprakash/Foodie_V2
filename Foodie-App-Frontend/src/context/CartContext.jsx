import { createContext, useState } from "react";
import ConfirmModal from "../components/ConfirmModal";
import toast from "react-hot-toast";

export const CartContext = createContext();

export const CartProvider = ({ children }) => {
  const [cart, setCart] = useState([]);
  const [pendingItem, setPendingItem] = useState(null);
  const [showModal, setShowModal] = useState(false);

  const addToCart = (item, restaurant) => {
    setCart((prev) => {
      if (prev.length === 0) {
        return [
          {
            ...item,
            qty: 1,
            restaurantId: restaurant.restaurantId,
            restaurantName: restaurant.restaurantName,
          },
        ];
      }

      if (prev[0].restaurantId !== restaurant.restaurantId) {
        setPendingItem({ item, restaurant });
        setShowModal(true);
        return prev;
      }

      const existing = prev.find(
        (i) => i.name === item.name && i.restaurantId === restaurant.restaurantId
      );

      if (existing) {
        return prev.map((i) =>
          i.name === item.name && i.restaurantId === restaurant.restaurantId
            ? { ...i, qty: i.qty + 1 }
            : i
        );
      }

      return [
        ...prev,
        {
          ...item,
          qty: 1,
          restaurantId: restaurant.restaurantId,
          restaurantName: restaurant.restaurantName,
        },
      ];
    });
  };

  const confirmClearAndAdd = () => {
    if (pendingItem) {
      setCart([
        {
          ...pendingItem.item,
          qty: 1,
          restaurantId: pendingItem.restaurant.restaurantId,
          restaurantName: pendingItem.restaurant.restaurantName,
        },
      ]);

      // ✅ Toast feedback
      toast.success(
        `Cart cleared and new items from ${pendingItem.restaurant.restaurantName} added!`, { position: "bottom-right" }
      );
    }
    setPendingItem(null);
    setShowModal(false);
  };

  const cancelClear = () => {
    setPendingItem(null);
    setShowModal(false);
  };

  const decreaseQty = (itemName, restaurantId) => {
    setCart((prev) =>
      prev
        .map((i) =>
          i.name === itemName && i.restaurantId === restaurantId
            ? { ...i, qty: i.qty - 1 }
            : i
        )
        .filter((i) => i.qty > 0)
    );
  };

  const removeFromCart = (itemName) => {
    setCart((prev) => prev.filter((i) => i.name !== itemName));
    toast("Item removed from cart 🗑️",{ position: "bottom-right"});
  };

  const clearCart = () => {
    setCart([]);
  };

  return (
    <CartContext.Provider
      value={{ cart, addToCart, decreaseQty, removeFromCart, clearCart }}
    >
      {children}

      <ConfirmModal
        show={showModal}
        title="Clear Cart?"
        message={`Your cart has items from ${cart[0]?.restaurantName}. Do you want to clear it and add items from ${pendingItem?.restaurant?.restaurantName}?`}
        onConfirm={confirmClearAndAdd}
        onCancel={cancelClear}
      />
    </CartContext.Provider>
  );
};
