package com.foodie.restaurant_service;

import com.foodie.restaurant_service.model.MenuItem;
import com.foodie.restaurant_service.model.Restaurant;
import com.foodie.restaurant_service.model.RestaurantDocument;
import com.foodie.restaurant_service.repository.RestaurantRepository;
import com.foodie.restaurant_service.repository.RestaurantSearchRepository;
import com.foodie.restaurant_service.service.RestaurantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestaurantServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantSearchRepository searchRepository;

    @InjectMocks
    private RestaurantService restaurantService;

    private Restaurant sampleRestaurant;

    @BeforeEach
    void setUp() {
        sampleRestaurant = Restaurant.builder()
                .restaurantId("rest-001")
                .restaurantName("Burger Palace")
                .cuisineType("American")
                .address("Hyderabad")
                .rating(4.5)
                .menu(List.of(MenuItem.builder()
                        .name("Burger")
                        .description("Classic beef burger")
                        .price(8.99)
                        .available(true)
                        .build()))
                .build();
    }

    @Test
    void getAll_returnsAllRestaurants() {
        when(restaurantRepository.findAll()).thenReturn(List.of(sampleRestaurant));

        List<Restaurant> result = restaurantService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRestaurantName()).isEqualTo("Burger Palace");
    }

    @Test
    void getByRestaurantId_returnsRestaurant_whenFound() {
        when(restaurantRepository.findByRestaurantId("rest-001")).thenReturn(sampleRestaurant);

        Restaurant result = restaurantService.getByRestaurantId("rest-001");

        assertThat(result).isNotNull();
        assertThat(result.getCuisineType()).isEqualTo("American");
    }

    @Test
    void getRestaurantByCity_returnsMatchingRestaurants() {
        when(restaurantRepository.findByAddress("Hyderabad")).thenReturn(List.of(sampleRestaurant));

        List<Restaurant> result = restaurantService.getRestaurantByCity("Hyderabad");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAddress()).isEqualTo("Hyderabad");
    }

    @Test
    void save_persistsRestaurantAndSyncsSearchIndex() {
        when(restaurantRepository.save(sampleRestaurant)).thenReturn(sampleRestaurant);
        when(searchRepository.save(any())).thenReturn(new RestaurantDocument());

        Restaurant result = restaurantService.save(sampleRestaurant);

        assertThat(result.getRestaurantId()).isEqualTo("rest-001");
        verify(restaurantRepository).save(sampleRestaurant);
        verify(searchRepository).save(argThat(doc ->
                doc.getRestaurantId().equals("rest-001") &&
                doc.getRestaurantName().equals("Burger Palace")
        ));
    }

    @Test
    void save_mapsMenuItems_toSearchDocument() {
        when(restaurantRepository.save(sampleRestaurant)).thenReturn(sampleRestaurant);
        when(searchRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        restaurantService.save(sampleRestaurant);

        verify(searchRepository).save(argThat(doc ->
                doc.getMenu() != null && !doc.getMenu().isEmpty()
        ));
    }

    @Test
    void delete_removesFromBothRepositories() {
        restaurantService.delete("rest-001");

        verify(restaurantRepository).deleteByRestaurantId("rest-001");
        verify(searchRepository).deleteById("rest-001");
    }

    @Test
    void filterByCuisine_returnsMatchingRestaurants() {
        when(restaurantRepository.findByCuisineType("American")).thenReturn(List.of(sampleRestaurant));

        List<Restaurant> result = restaurantService.filterByCuisine("American");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCuisineType()).isEqualTo("American");
    }

    @Test
    void searchRestaurants_withAddress_usesLocationAwareSearch() {
        RestaurantDocument doc = new RestaurantDocument();
        doc.setRestaurantId("rest-001");
        when(searchRepository.searchFuzzyWithCityFilter("Burger", "Hyderabad")).thenReturn(List.of(doc));

        List<RestaurantDocument> result = restaurantService.searchRestaurants("Burger", "Hyderabad");

        assertThat(result).hasSize(1);
        verify(searchRepository).searchFuzzyWithCityFilter("Burger", "Hyderabad");
        verify(searchRepository, never()).searchFuzzyAcrossAllFields(anyString());
    }

    @Test
    void searchRestaurants_withoutAddress_usesGlobalSearch() {
        when(searchRepository.searchFuzzyAcrossAllFields("Pizza")).thenReturn(List.of());

        restaurantService.searchRestaurants("Pizza", null);

        verify(searchRepository).searchFuzzyAcrossAllFields("Pizza");
        verify(searchRepository, never()).searchFuzzyWithCityFilter(anyString(), anyString());
    }

    @Test
    void searchRestaurants_withBlankAddress_usesGlobalSearch() {
        when(searchRepository.searchFuzzyAcrossAllFields("Sushi")).thenReturn(List.of());

        restaurantService.searchRestaurants("Sushi", "   ");

        verify(searchRepository).searchFuzzyAcrossAllFields("Sushi");
    }
}
