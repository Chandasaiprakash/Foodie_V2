// in restaurant-service: .../repository/RestaurantSearchRepository.java
package com.foodie.restaurant_service.repository;

import com.foodie.restaurant_service.model.RestaurantDocument;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import java.util.List;

public interface RestaurantSearchRepository extends ElasticsearchRepository<RestaurantDocument, String> {

    /**
     * This advanced query does two things:
     * 1. Searches for the query in the top-level 'name' and 'cuisineType' fields.
     * 2. Uses a 'nested' query to also search inside the 'menuItems' list, looking at the 'name' and 'description' of each menu item.
     * A restaurant will be returned if EITHER of these conditions is met.
     */
    @Query("{" +
            "  \"bool\": {" +
            "    \"should\": [" +
            "      {" +
            "        \"multi_match\": {" +
            "          \"query\": \"?0\"," +
            "          \"fields\": [\"restaurantName\", \"cuisineType\"]," +
            "          \"fuzziness\": \"AUTO\"" +
            "        }" +
            "      }," +
            "      {" +
            "        \"nested\": {" +
            "          \"path\": \"menu\"," +
            "          \"query\": {" +
            "            \"multi_match\": {" +
            "              \"query\": \"?0\"," +
            "              \"fields\": [\"menu.name\", \"menu.description\"]," +
            "              \"fuzziness\": \"AUTO\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    ]" +
            "  }" +
            "}")
    List<RestaurantDocument> searchFuzzyAcrossAllFields(String query);
    // ✨ NEW LOCATION-AWARE QUERY
    @Query("{" +
            "  \"bool\": {" +
            "    \"must\": [" + // All conditions in 'must' must be true
            "      {" +
            "        \"term\": { \"address.keyword\": \"?1\" }" + // 1. Filter by city (exact match)
            "      }" +
            "    ]," +
            "    \"should\": [" + // Then, one of these 'should' conditions should be true
            "      {" +
            "        \"multi_match\": {" +
            "          \"query\": \"?0\"," +
            "          \"fields\": [\"restaurantName\", \"cuisineType\"]," +
            "          \"fuzziness\": \"AUTO\"" +
            "        }" +
            "      }," +
            "      {" +
            "        \"nested\": {" +
            "          \"path\": \"menu\"," +
            "          \"query\": {" +
            "            \"multi_match\": {" +
            "              \"query\": \"?0\"," +
            "              \"fields\": [\"menu.name\", \"menu.description\"]," +
            "              \"fuzziness\": \"AUTO\"" +
            "            }" +
            "          }" +
            "        }" +
            "      }" +
            "    ]," +
            "    \"minimum_should_match\": 1" + // At least one of the 'should' clauses must match
            "  }" +
            "}")
    List<RestaurantDocument> searchFuzzyWithCityFilter(String query, String address);
}