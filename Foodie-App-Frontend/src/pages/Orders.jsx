import { useEffect, useState, useContext, Fragment } from "react";
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";
import { ToastContainer, toast } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";
import { AuthContext } from "../context/AuthProvider";
import api from "../utils/api";
import notificationSound from "../sounds/notification.mp3";
import { motion, AnimatePresence } from "framer-motion";
import { Menu, Transition } from "@headlessui/react";
import { CartContext } from "../context/CartContext";
import ConfirmationModal from '../components/ConfirmationModal';
import { WS_BASE_URL } from "../utils/apiConfig";
import {
  TruckIcon,
  CheckCircleIcon,
  XCircleIcon,
  ClockIcon,
  ChevronRightIcon,
  // RENAME: DotsVerticalIcon is now EllipsisVerticalIcon in v2
  EllipsisVerticalIcon,
  CurrencyRupeeIcon,
} from "@heroicons/react/24/outline";
  import { useNavigate } from "react-router-dom";



const Orders = () => {
  const [orders, setOrders] = useState([]);
  const [highlightedOrder, setHighlightedOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const { user } = useContext(AuthContext);

  
const { addToCart, clearCart } = useContext(CartContext); // 👈 Add this near top

const navigate = useNavigate();

  // --- Helper Functions ---

  const playSound = () => {
    const audio = new Audio(notificationSound);
    audio.play().catch(() => {});
  };
  const [modalState, setModalState] = useState({
    show: false,
    title: "",
    message: "",
    onConfirm: () => {},
  });

  const hideModal = () => setModalState({ ...modalState, show: false });




const handleReorder = (order) => {
    setModalState({
      show: true,
      title: "Reorder Items?",
      message: "This will clear your current cart and add all items from this previous order. Do you want to continue?",
      onConfirm: () => {
        hideModal();
        try {
          if (!order.items || order.items.length === 0) {
            toast.error("No items found to reorder!");
            return;
          }
          clearCart();
          order.items.forEach((item) => {
            addToCart(
              { name: item.name, price: item.price, qty: item.quantity || 1 },
              { restaurantId: order.restaurantId, restaurantName: order.restaurantName }
            );
          });
          toast.success("🛒 Previous order items added to your cart!");
          setTimeout(() => navigate("/cart"), 800);
        } catch (err) {
          toast.error("Failed to reorder. Please try again.");
        }
      },
    });
  };



  const showToast = (event) => {
    const statusText = event.status || event.paymentStatus;
    // Use substring for a shorter, cleaner ID in the toast
    const orderIdShort = event.orderUuid ? event.orderUuid.substring(0, 8) + '...' : 'N/A';
    const msg = `Order ${orderIdShort} → ${statusText}`;
    playSound();

    if (event.status === "DELIVERED")
      toast.success(`🎉 Delivered! ${msg}`, { autoClose: 5000 });
    else if (["ON_THE_WAY", "PICKED_UP"].includes(event.status))
      toast.info(`🚚 Status Update: ${msg}`);
    else if (event.paymentStatus === "SUCCESS")
      toast.success(`💳 Payment Success: ${orderIdShort}`);
    else if (["FAILED", "CANCELLED"].includes(event.status))
      toast.error(`💔 ${msg}`, { autoClose: 7000 });
    else toast.info(`🔔 Status Update: ${msg}`);
  };

  const getStatusInfo = (status, paymentStatus) => {
    switch (status) {
      case "DELIVERED":
        return {
          text: "Delivered",
          color: "text-green-600 bg-green-100",
          icon: CheckCircleIcon,
          highlight: "#d1fae5", // Green-100
        };
      case "ON_THE_WAY":
      case "PICKED_UP":
        return {
          text: status === "ON_THE_WAY" ? "Out for Delivery" : "Picked Up",
          color: "text-indigo-600 bg-indigo-100",
          icon: TruckIcon,
          highlight: "#e0e7ff", // Indigo-100
        };
      case "CANCELLED":
      case "FAILED":
        return {
          text: status === "CANCELLED" ? "Cancelled" : "Failed",
          color: "text-red-600 bg-red-100",
          icon: XCircleIcon,
          highlight: "#fee2e2", // Red-100
        };
      default:
        // PENDING, READY, etc.
        if (paymentStatus === "SUCCESS") {
          return {
            text: status || "Processing",
            color: "text-yellow-600 bg-yellow-100",
            icon: ClockIcon,
            highlight: "#fffbe6", // Yellow-100
          };
        }
        return {
          text: status || "Unknown",
          color: "text-gray-500 bg-gray-100",
          icon: ClockIcon,
          highlight: "#f3f4f6", // Gray-100
        };
    }
  };

  // --- Data Fetching Effect (Initial Load) ---

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    const token = localStorage.getItem("token");

    api
      .get(`/orders/customer/${user.email}`, {
        headers: {
          Authorization: `Bearer ${token}`,
          "X-User-Email": user.email,
        },
      })
      .then((res) => {
        // Sort orders by date descending (newest first)
        const sortedOrders = res.data.sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate));
        setOrders(sortedOrders);
        setLoading(false);
      })
      .catch((err) => {
        console.error("❌ Error loading orders:", err);
        toast.error("Failed to load orders. Please try again.");
        setLoading(false);
      });
  }, [user]);

  // --- WebSocket Live Updates Effect (FIXED LOGIC) ---
  useEffect(() => {
    if (!user) return;
    const socket = new SockJS(WS_BASE_URL);
    const client = new Client({
      webSocketFactory: () => socket,
      reconnectDelay: 5000,
      debug: (str) => console.log(`[STOMP Debug] ${str}`),
      onConnect: () => {
        console.log("✅ Connected to WebSocket");

        // Subscribe to updates topic
        client.subscribe("/topic/updates", (message) => {
          if (message.body) {
            const event = JSON.parse(message.body);

            if (event.customerEmail === user.email) {
              showToast(event);
              setHighlightedOrder(event.orderUuid);

              // ✅ CRITICAL FIX: Update and re-sort within the state setter
              setOrders((prevOrders) => {
                let updatedOrderFound = false;
                const newOrders = prevOrders.map((order) => {
                  if (order.orderUuid === event.orderUuid) {
                    updatedOrderFound = true;
                    // Merge new event data into the existing order
                    return { ...order, ...event };
                  }
                  return order;
                });

                // If a new order was created and pushed through the stream (e.g., status: CREATED)
                // You might need to add logic here to *add* the event to the list if it wasn't found.
                // Assuming orders are only updated, not created via this stream for now.

                // Re-sort the list to put the newly updated item at the top visually
                return newOrders.sort((a, b) => {
                  // Prioritize the recently updated order
                  if (a.orderUuid === event.orderUuid) return -1;
                  if (b.orderUuid === event.orderUuid) return 1;
                  // Otherwise, sort by date descending
                  return new Date(b.orderDate) - new Date(a.orderDate);
                });
              });

              // Remove highlight after 3 seconds
              setTimeout(() => {
                setHighlightedOrder(null);
              }, 3000);
            }
          }
        });
      },
      onStompError: (frame) => {
        console.error(`Broker reported error: ${frame.headers['message']}`);
        console.error(`Additional details: ${frame.body}`);
        toast.error("WebSocket connection error. Real-time updates paused.");
      },
    });

    client.activate();
    // Cleanup function
    return () => client.deactivate();
  }, [user]); // Depend on user to re-establish connection if user changes

  // --- Action Handlers ---

  const handleDeleteOrder = (orderUuid) => {
    setModalState({
      show: true,
      title: "Delete Order History?",
      message: "Are you sure you want to permanently delete this order? This action cannot be undone.",
      onConfirm: async () => {
        hideModal();
        try {
          const token = localStorage.getItem("token");
          await api.delete(`/orders/${orderUuid}`, {
            headers: { Authorization: `Bearer ${token}`, "X-User-Email": user.email },
          });
          setOrders((prev) => prev.filter((o) => o.orderUuid !== orderUuid));
          setSelectedOrder(null);
          toast.info("🗑️ Order history deleted.");
        } catch (err) {
          toast.error("❌ Failed to delete order.");
        }
      },
    });
  };

  // --- Render Logic ---

  if (loading)
    return (
      <div className="flex flex-col items-center justify-center h-[70vh] text-gray-500">
        <ClockIcon className="w-8 h-8 animate-spin mb-2 text-indigo-500" />
        <p className="text-lg font-medium">Fetching your orders...</p>
      </div>
    );

  // Component for formatting price
  const PriceDisplay = ({ amount }) => (
    <span className="font-bold text-gray-800 flex items-center">
      <CurrencyRupeeIcon className="w-4 h-4 mr-0.5" />
      {parseFloat(amount).toFixed(2)}
    </span>
  );

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h2 className="text-3xl font-extrabold mb-6 text-gray-900 border-b pb-2">
        My Order History
      </h2>

      <ul className="space-y-4">
        <AnimatePresence initial={false}>
          {orders.length === 0 ? (
            <div className="text-center py-12 bg-gray-50 rounded-lg">
              <p className="text-xl text-gray-500 font-medium">
                You haven't placed any orders yet.
              </p>
              <p className="text-sm text-gray-400 mt-1">
                Time to find a restaurant!
              </p>
            </div>
          ) : (
            orders.map((order) => {
              const isHighlighted = highlightedOrder === order.orderUuid;
              const { text, color, icon: StatusIcon, highlight } = getStatusInfo(
                order.status,
                order.paymentStatus
              );

              const itemNames = order.items
                ? order.items.map((i) => i.name).slice(0, 2).join(", ")
                : "Items not available";

              return (
                <motion.li
                  key={order.orderUuid}
                  initial={{ opacity: 0, y: -20 }}
                  animate={{
                    opacity: 1,
                    y: 0,
                    // Use CSS transition for a smoother background color change
                    backgroundColor: isHighlighted ? highlight : "#ffffff",
                    scale: isHighlighted ? 1.01 : 1,
                  }}
                  exit={{ opacity: 0, height: 0 }}
                  transition={{
                    // Spring for the "pop" on highlight, easeInOut otherwise
                    type: isHighlighted ? "spring" : "easeInOut",
                    duration: isHighlighted ? 0.4 : 0.2,
                  }}
                  className="p-4 border border-gray-200 rounded-xl shadow-sm hover:shadow-lg transition-all cursor-pointer flex justify-between items-center"
                  onClick={() => setSelectedOrder(order)}
                >
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold text-lg text-gray-900 truncate">
                      {order.restaurantName || "Unknown Restaurant"}
                    </p>
                    <p className="text-gray-500 text-sm truncate mt-0.5">
                      {itemNames}
                      {order.items && order.items.length > 2 && (
                        <span className="text-gray-400"> +{order.items.length - 2} items</span>
                      )}
                    </p>
                  </div>

                  <div className="flex items-center space-x-4 ml-4">
                    {/* Status Badge */}
                    <span
                      className={`inline-flex items-center px-3 py-1.5 rounded-full text-xs font-semibold uppercase ${color}`}
                    >
                      <StatusIcon className="w-4 h-4 mr-1" />
                      {text}
                    </span>
                    
                    {/* Price */}
                    <PriceDisplay amount={order.total} />

                    {/* Menu Button */}
                    <Menu as="div" className="relative inline-block text-left z-10">
                      <div>
                        <Menu.Button
                          className="p-1 rounded-full text-gray-400 hover:text-gray-600 hover:bg-gray-50 focus:outline-none"
                          onClick={(e) => e.stopPropagation()} // Prevent list item click
                        >
                          <EllipsisVerticalIcon className="w-5 h-5" />
                        </Menu.Button>
                      </div>

                      <Transition
                        as={Fragment}
                        enter="transition ease-out duration-100"
                        enterFrom="transform opacity-0 scale-95"
                        enterTo="transform opacity-100 scale-100"
                        leave="transition ease-in duration-75"
                        leaveFrom="transform opacity-100 scale-100"
                        leaveTo="transform opacity-0 scale-95"
                      >
                        <Menu.Items className="absolute right-0 w-48 mt-2 origin-top-right bg-white border border-gray-200 divide-y divide-gray-100 rounded-md shadow-xl focus:outline-none">
                          <div className="py-1">
                            <Menu.Item>
                              {({ active }) => (
                                <button
                                  className={`${
                                    active ? "bg-indigo-50 text-indigo-700" : "text-gray-700"
                                  } group flex items-center w-full px-4 py-2 text-sm`}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    setSelectedOrder(order);
                                  }}
                                >
                                  <ChevronRightIcon className="w-4 h-4 mr-2" />
                                  View Details
                                </button>
                              )}
                            </Menu.Item>
                            <Menu.Item>
  {({ active }) => (
    <button
      className={`${
        active ? "bg-green-50 text-green-700" : "text-green-600"
      } group flex items-center w-full px-4 py-2 text-sm`}
      onClick={(e) => {
        e.stopPropagation();
        handleReorder(order); // ✅ use 'order', not 'selectedOrder'
      }}
    >
      <ChevronRightIcon className="w-4 h-4 mr-2" />
      Reorder
    </button>
  )}
</Menu.Item>

                            <Menu.Item>
                              {({ active }) => (
                                <button
                                  className={`${
                                    active ? "bg-red-50 text-red-700" : "text-red-600"
                                  } group flex items-center w-full px-4 py-2 text-sm`}
                                  onClick={(e) => {
                                    e.stopPropagation();
                                    handleDeleteOrder(order.orderUuid);
                                  }}
                                >
                                  <XCircleIcon className="w-4 h-4 mr-2" />
                                  Delete History
                                </button>
                              )}
                            </Menu.Item>
                          </div>
                        </Menu.Items>
                      </Transition>
                    </Menu>
                  </div>
                </motion.li>
              );
            })
          )}
        </AnimatePresence>
      </ul>

      {/* 🧾 Order Details SLIDE-OVER Panel */}
      <AnimatePresence>
        {selectedOrder && (
          <motion.div
            className="fixed inset-0 overflow-hidden z-50"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
          >
            <div className="absolute inset-0 overflow-hidden">
              {/* Overlay for closing */}
              <motion.div
                className="absolute inset-0 bg-gray-500 bg-opacity-75 transition-opacity"
                onClick={() => setSelectedOrder(null)}
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
              />

              <div className="fixed inset-y-0 right-0 max-w-full flex">
                <motion.div
                  className="w-screen max-w-md"
                  initial={{ x: "100%" }}
                  animate={{ x: 0 }}
                  exit={{ x: "100%" }}
                  transition={{ type: "tween", duration: 0.3 }}
                >
                  <div className="h-full flex flex-col bg-white shadow-xl overflow-y-scroll">
                    <div className="p-6 border-b">
                      <h2 className="text-xl font-bold text-gray-900">
                        Order  
                        <span className="text-sm"> #
    {selectedOrder.orderUuid}
  </span>
                      </h2>
                      <p className="text-sm text-gray-500">
                        From: {selectedOrder.restaurantName}
                      </p>
                    </div>

                    <div className="relative flex-1 p-6 space-y-6">
                      {/* Status and Payment */}
                      <div>
                        <h3 className="text-md font-semibold text-gray-900 mb-2">Order Summary</h3>
                        <dl className="space-y-2 text-sm">
                          <div className="flex justify-between items-center border-b pb-2">
                            <dt className="font-medium text-gray-700">Status</dt>
                            <dd>
                              <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold ${getStatusInfo(selectedOrder.status).color}`}>
                                {getStatusInfo(selectedOrder.status).text}
                              </span>
                            </dd>
                          </div>
                          <div className="flex justify-between items-center border-b pb-2">
                            <dt className="font-medium text-gray-700">Payment</dt>
                            <dd className={selectedOrder.paymentStatus === "SUCCESS" ? "text-green-600 font-semibold" : "text-red-600 font-semibold"}>
                              {selectedOrder.paymentStatus || "N/A"}
                            </dd>
                          </div>
                          <div className="flex justify-between items-center border-b pb-2">
                            <dt className="font-medium text-gray-700">Created At</dt>
                            <dd className="text-gray-700">
                              {new Date(selectedOrder.createdAt).toLocaleString()}
                            </dd>
                          </div>
                          <div className="flex justify-between items-center">
                            <dt className="font-medium text-gray-700">Customer Email</dt>
                            <dd className="text-gray-700 text-xs truncate max-w-[150px]">{selectedOrder.customerEmail}</dd>
                          </div>
                        </dl>
                      </div>

                      {/* Items List */}
                      <div>
                        <h3 className="text-md font-semibold text-gray-900 mb-2">Items</h3>
                        <ul className="space-y-2 divide-y">
                          {selectedOrder.items && selectedOrder.items.length > 0 ? (
                            selectedOrder.items.map((item, idx) => (
                              <li key={idx} className="flex justify-between text-sm pt-2">
                                <span className="text-gray-700">
                                  {item.name} <span className="text-gray-500">× {item.quantity || item.qty || 1}</span>
                                </span>
                                <span className="font-medium text-gray-800">
                                  ₹ {parseFloat(item.price * (item.quantity || item.qty || 1)).toFixed(2)}
                                </span>
                              </li>
                            ))
                          ) : (
                            <li className="text-gray-500 italic">No items listed</li>
                          )}
                        </ul>
                      </div>

                      {/* Total */}
                      <div className="pt-4 border-t-2 border-dashed">
                        <div className="flex justify-between text-lg font-bold">
                          <span>Total Amount</span>
                          <PriceDisplay amount={selectedOrder.total} />
                        </div>
                      </div>
                    </div>

                    <div className="p-6 border-t flex justify-between space-x-3">
                        <button
                          onClick={() => handleDeleteOrder(selectedOrder.orderUuid)}
                          className="flex-1 px-4 py-2 text-sm font-semibold text-red-600 border border-red-300 rounded-lg hover:bg-red-50 transition"
                        >
                          Delete History
                        </button>
                        <button
    onClick={() => 
       
        handleReorder(selectedOrder)
      }
    
    className="flex-1 px-4 py-2 text-sm font-semibold text-green-600 border border-green-300 rounded-lg hover:bg-green-50 transition"
  >
    Reorder
  </button>
                        <button
                          onClick={() => setSelectedOrder(null)}
                          className="flex-1 px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 text-sm font-semibold shadow-md transition"
                        >
                          Close
                        </button>
                    </div>
                  </div>
                </motion.div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
<ConfirmationModal
        show={modalState.show}
        title={modalState.title}
        message={modalState.message}
        onConfirm={modalState.onConfirm}
        onCancel={hideModal}
      />
      <ToastContainer position="bottom-right" newestOnTop={true} />
    </div>
  );
};

export default Orders;
