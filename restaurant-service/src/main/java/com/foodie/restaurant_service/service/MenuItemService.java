package com.foodie.restaurant_service.service;

import com.foodie.restaurant_service.model.MenuItem;
import com.foodie.restaurant_service.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;

    public List<MenuItem> getByRestaurant(String restaurantName) {
        return menuItemRepository.findByRestaurantName(restaurantName);
    }

    public MenuItem add(MenuItem menuItem) {
        return menuItemRepository.save(menuItem);
    }

    public MenuItem update(MenuItem menuItem) {
        return menuItemRepository.save(menuItem);
    }

    public void delete(String id) {
        menuItemRepository.deleteById(id);
    }
}
