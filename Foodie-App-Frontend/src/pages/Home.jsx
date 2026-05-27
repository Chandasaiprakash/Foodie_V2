import { motion } from "framer-motion";
import PopularCuisines from "../components/PopularCuisines";
import SearchBar from "../components/SearchBar";


// Removed Link import as it's causing an error without a Router context
import React from 'react'; // Added React import for context

// --- Helper Components ---
// To resolve the error, these components are now defined directly in this file.

// PageWrapper: A simple layout component.
const PageWrapper = ({ children }) => (
  <main>{children}</main>
);





// --- Main Home Component ---

// Animation variants for the content
const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.2,
    },
  },
};

const itemVariants = {
  hidden: { y: 20, opacity: 0 },
  visible: {
    y: 0,
    opacity: 1,
    transition: {
      duration: 0.6,
      ease: "easeOut",
    },
  },
};

export default function Home() {
  return (
    <PageWrapper >
      {/* HERO SECTION */}
      <section className="relative flex min-h-[85vh] items-center justify-center overflow-hidden px-4 py-12 text-center">
        {/* Background Image */}
        <img
          src="https://images.unsplash.com/photo-1504674900247-0877df9cc836?q=80&w=2070&auto=format&fit=crop"
          alt="A background image of a delicious food platter"
          className="absolute inset-0 z-0 h-full w-full object-cover"
          loading="lazy"
          decoding="async"
        />

        {/* Dimming Overlay */}
        <div className="absolute inset-0 z-10 bg-black/60" aria-hidden="true" />

        {/* Content Container */}
        <motion.div
          className="relative z-20 flex flex-col items-center"
          variants={containerVariants}
          initial="hidden"
          animate="visible"
        >
          <motion.h1
            variants={itemVariants}
            className="text-4xl font-extrabold tracking-tight text-white sm:text-5xl md:text-6xl"
          >
            <span className="block">Craving Something</span>
            <span className="block bg-gradient-to-r from-rose-500 to-pink-500 bg-clip-text text-transparent">
              Delicious?
            </span>
          </motion.h1>

          <motion.p
            variants={itemVariants}
            className="mx-auto mt-4 max-w-md text-lg text-gray-200"
          >
            Find and order from thousands of local restaurants, all in one place. Your next favorite meal is just a few clicks away.
          </motion.p>

          <motion.div variants={itemVariants} className="mt-8 mt-8 w-full">
            <SearchBar />
          </motion.div>

          <motion.div variants={itemVariants} className="mt-6">
            <p className="text-sm text-gray-300">
              or{" "}
              {/* FIX: Replaced <Link> with <a> to prevent crash when not inside a Router. */}
              <a href="/restaurants" className="font-medium text-rose-400 hover:underline">
                Browse All Restaurants
              </a>
            </p>
          </motion.div>
        </motion.div>
      </section>

      {/* POPULAR CUISINES SECTION */}
      <section className="container mx-auto px-4 pt-4 md:px-8 md:pt-8">
        <div className="container mx-auto px-4 text-center">
          <h2 className="text-3xl font-bold tracking-tight text-gray-800">
            Explore by Cuisine
          </h2>
          <p className="mt-2 text-gray-500">Discover new flavors and find your favorites.</p>
          <div className="mt-12">
            <PopularCuisines />
          </div>
        </div>
      </section>
    </PageWrapper>
  );
}

