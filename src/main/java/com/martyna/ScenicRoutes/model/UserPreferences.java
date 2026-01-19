package com.martyna.ScenicRoutes.model;

import java.util.HashMap;
import java.util.Map;

public class UserPreferences {

    private RouteShape routeShape = RouteShape.LOOP;
    private Double endLat;
    private Double endLng;
    private Map<POICategory, Double> categoryWeights = new HashMap<>();

    public UserPreferences() {
        // Default: all categories have weight 1.0 (neutral)
        for (POICategory category : POICategory.values()) {
            categoryWeights.put(category, 1.0);
        }
    }

    // Route shape
    public enum RouteShape {
        LOOP,
        ONE_WAY,
        POINT_TO_POINT
    }

    // Enhanced categories
    public enum POICategory {
        // Visual & Photo Spots
        LANDMARK("landmark", "tourist_attraction", "point_of_interest"),
        PARK("park"),
        STREET_ART("public_art", "art_gallery"),
        VIEWPOINT("viewpoint", "observation_deck"),
        FOUNTAIN("fountain"),
        SCULPTURE("monument", "statue"),

        // Cultural
        HISTORIC_SITE("historic_site", "heritage_site"),
        CHURCH("church", "place_of_worship", "synagogue", "mosque", "temple"),
        THEATER("theater", "performing_arts_theater"),

        // Urban
        SHOPPING("shopping_mall", "store", "clothing_store"),
        CITY_SQUARE("plaza", "town_square"),

        // Food & Drink
        CAFE("cafe", "coffee_shop"),
        RESTAURANT("restaurant"),

        // For sightseeing
        MUSEUM("museum"),
        AQUARIUM("aquarium"),
        ZOO("zoo"),
        OBSERVATION_DECK("observation_deck"),

        // Popularity-based
        HIDDEN_GEM("hidden_gem"),
        TRENDING("trending");

        private final String[] googleTypes;

        POICategory(String... googleTypes) {
            this.googleTypes = googleTypes;
        }

        public String[] getGoogleTypes() {
            return googleTypes;
        }

        public static POICategory fromGoogleType(String googleType) {
            for (POICategory category : values()) {
                for (String type : category.googleTypes) {
                    if (type.equals(googleType)) {
                        return category;
                    }
                }
            }
            throw new IllegalArgumentException("Unknown Google type: " + googleType);
        }

        public boolean matchesGoogleType(String googleType) {
            for (String type : googleTypes) {
                if (type.equals(googleType)) return true;
            }
            return false;
        }
    }

    // Getters and setters
    public RouteShape getRouteShape() {
        return routeShape;
    }

    public void setRouteShape(RouteShape routeShape) {
        this.routeShape = routeShape;
    }

    public Double getEndLat() {
        return endLat;
    }

    public void setEndLat(Double endLat) {
        this.endLat = endLat;
    }

    public Double getEndLng() {
        return endLng;
    }

    public void setEndLng(Double endLng) {
        this.endLng = endLng;
    }

    public boolean hasEndPoint() {
        return endLat != null && endLng != null;
    }

    public Map<POICategory, Double> getCategoryWeights() {
        return new HashMap<>(categoryWeights);
    }

    public double getCategoryWeight(POICategory category) {
        return categoryWeights.getOrDefault(category, 1.0);
    }

    public void setCategoryWeight(POICategory category, double weight) {
        categoryWeights.put(category, weight);
    }

    //Check if user has selected any preferences
    public boolean hasCustomPreferences() {
        for (double weight : categoryWeights.values()) {
            if (Math.abs(weight - 1.0) != 0) return true;
        }
        return false;
    }

    //Get all categories that user has boosted (weight > 1.0)
    public Map<POICategory, Double> getBoostedCategories() {
        Map<POICategory, Double> boosted = new HashMap<>();
        for (Map.Entry<POICategory, Double> entry : categoryWeights.entrySet()) {
            if (entry.getValue() > 1.0) {
                boosted.put(entry.getKey(), entry.getValue());
            }
        }
        return boosted;
    }
}
