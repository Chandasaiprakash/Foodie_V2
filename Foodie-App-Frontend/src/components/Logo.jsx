import { Link } from 'react-router-dom';
import logoImage from '../images/LightLogo.jpg'; // Import the logo image

export default function Logo() {
  return (
    <Link to="/" className="flex items-center gap-1 group">
      {/* The Logo Image */}
      <img 
        src={logoImage} 
        alt="Foodie Logo" 
        className="h-12 w-auto" // Slightly adjusted height
      />
      {/* The Brand Name */}
      <span className="text-3xl font-bold text-rose-600 transition-colors group-hover:text-rose-700">
        Foodie
      </span>
    </Link>
  );
}