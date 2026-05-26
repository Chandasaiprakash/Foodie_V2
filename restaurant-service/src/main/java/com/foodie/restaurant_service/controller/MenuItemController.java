package com.foodie.restaurant_service.controller;

import com.foodie.restaurant_service.model.MenuItem;
import com.foodie.restaurant_service.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu-items")
@RequiredArgsConstructor
public class MenuItemController {

    private final MenuItemService menuItemService;

    @GetMapping("/restaurant/{restaurantName}")
    public List<MenuItem> getByRestaurant(@PathVariable String restaurantName) {
        return menuItemService.getByRestaurant(restaurantName);
    }

    @PostMapping
    public MenuItem add(@RequestBody MenuItem menuItem) {
        return menuItemService.add(menuItem);
    }

    @PutMapping("/{id}")
    public MenuItem update(@PathVariable String id, @RequestBody MenuItem menuItem) {
        menuItem.setId(id);
        return menuItemService.update(menuItem);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        menuItemService.delete(id);
    }
}
