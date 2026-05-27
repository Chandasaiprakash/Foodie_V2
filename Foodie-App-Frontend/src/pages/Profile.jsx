// src/pages/Profile.jsx
import { useContext } from "react";
import { AuthContext } from "../context/AuthProvider";

export default function Profile() {
  const { user } = useContext(AuthContext);

  if (!user) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <p className="text-gray-600 text-lg">No user data available.</p>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-100">
      <div className="bg-white p-8 rounded-xl shadow-md w-full max-w-md">
        <h2 className="text-2xl font-bold mb-6 text-center text-rose-600">
          My Profile
        </h2>

        <div className="space-y-4">
          <div className="flex justify-between">
            <span className="font-semibold">Email:</span>
            <span>{user.email}</span>
          </div>
          <div className="flex justify-between">
            <span className="font-semibold">Phone Number:</span>
            <span>{user.phoneNumber}</span>
          </div>

          <div className="flex justify-between">
            <span className="font-semibold">Role:</span>
            <span>{user.role}</span>
          </div>

        </div>
      </div>
    </div>
  );
}
