import { useNavigate } from "react-router-dom";
import Biryani from "../images/Biryani.png";
import Pizza from "../images/Pizza.png";
import Rolls from "../images/Rolls.png";
import Tea from "../images/Tea.png";
import Burger from "../images/Burger.png";
import Chinese from "../images/Chinese.png";
import Cake from "../images/Cake.png";
import Dessert from "../images/Dessert.png";
import NorthIndian from "../images/NorthIndian.png";
import SouthIndian from "../images/SouthIndian.png";
import Sandwich from "../images/Sandwich.png";
import Icecream from "../images/Icecream.png";
import haleem from "../images/haleem.png";
import momo from "../images/momo.png";
import milkshake from "../images/milkshake.png";
import pasta from "../images/pasta.png";
import juice from "../images/juice.png";

// Data for the popular cuisines
// Data for the popular cuisines — using Unsplash source images (reliable)
const cuisines = [
  { name: 'Biryani',imageUrl: Biryani},
  { name: 'Pizzas',imageUrl: Pizza },
  { name: 'Rolls',imageUrl: Rolls },
  { name: 'Haleem',imageUrl: haleem  },
  { name: 'Tea',imageUrl: Tea },
  { name: 'Burger',imageUrl: Burger },
  { name: 'Chinese',imageUrl:Chinese },
  { name: 'Cake',imageUrl:Cake },
  { name: 'Dessert', imageUrl:Dessert },
  { name: 'North Indian',imageUrl: NorthIndian },
  { name: 'South Indian',imageUrl: SouthIndian  },
  { name: 'Sandwich',imageUrl: Sandwich},
  { name: 'Ice cream',imageUrl: Icecream  },
  {name: 'Momo', imageUrl: momo},
  {name: 'Milkshake', imageUrl: milkshake},
  {name: 'Pasta', imageUrl: pasta},
  {name: 'Juice', imageUrl: juice}

];


export default function PopularCuisines() {
  const navigate = useNavigate();

  const handleCuisineClick = (cuisineName) => {
    navigate(`/search-results?q=${cuisineName}`);
  };

  return (
    <div className="mt-12">
      <div className="flex items-center space-x-6 overflow-x-auto pb-4">
        {cuisines.map((cuisine) => (
          <div 
            key={cuisine.name} 
            className="flex flex-col items-center flex-shrink-0 cursor-pointer group"
            onClick={() => handleCuisineClick(cuisine.name)}
          >
            <img 
              src={cuisine.imageUrl} 
              alt={cuisine.name} 
              className="w-20 h-20 rounded-full object-cover shadow-md transition-transform duration-200 group-hover:scale-105" 
            />
            <p className="mt-2 text-sm font-medium text-gray-700">{cuisine.name}</p>
          </div>
        ))}
      </div>
    </div>
  );
}