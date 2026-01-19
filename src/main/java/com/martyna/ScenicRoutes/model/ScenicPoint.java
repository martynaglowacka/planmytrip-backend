package com.martyna.ScenicRoutes.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScenicPoint {
    private final String name;
    private final double latitude;
    private final double longitude;
    private final double score;
    private final List<String> types;
    private final int reviewCount;
    private final double rating;
    private String photoUrl;

    public ScenicPoint(String name, double latitude, double longitude, double score) {
        this(name, latitude, longitude, score, new ArrayList<>(), 0, 0.0);
    }

    public ScenicPoint(String name, double latitude, double longitude, double score, List<String> types) {
        this(name, latitude, longitude, score, types, 0, 0.0);
    }

    public ScenicPoint(String name, double latitude, double longitude, double score, List<String> types, int reviewCount) {
        this(name, latitude, longitude, score, types, reviewCount, 0.0);
    }

    public ScenicPoint(String name, double latitude, double longitude, double score, List<String> types, int reviewCount, double rating) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.score = score;
        this.types = types != null ? new ArrayList<>(types) : new ArrayList<>();
        this.reviewCount = reviewCount;
        this.rating = rating;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getScore() {
        return score;
    }

    public List<String> getTypes() {
        return new ArrayList<>(types);
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public double getRating() {
        return rating;
    }

    public boolean hasType(String type) {
        return types.contains(type);
    }
    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public UserPreferences.POICategory getPrimaryCategory() {
        // Debug logging for Empire State Building
        if (name != null && name.contains("Empire State")) {
            System.out.println("DEBUG Empire State Building types: " + types);
        }

        // hardcoded observation decks google doesn't catch
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("empire state building") ||
                    lowerName.contains("top of the rock") ||
                    lowerName.contains("one world observatory") ||
                    (lowerName.contains("observation"))) {
                return UserPreferences.POICategory.OBSERVATION_DECK;
            }
        }
        // check types
        if (types.contains("observation_deck")) {
            return UserPreferences.POICategory.OBSERVATION_DECK;
        }
        for (String type : types) {
            if (type.equals("museum"))  return UserPreferences.POICategory.MUSEUM;
            if (type.equals("zoo")) return UserPreferences.POICategory.ZOO;
            if (type.equals("aquarium")) return UserPreferences.POICategory.AQUARIUM;
        }

        for (String type : types) {
            // Skips generic types - we'll check these last
            if (type.equals("landmark") || type.equals("tourist_attraction") ||
                    type.equals("point_of_interest") || type.equals("establishment"))
                continue;

            for (UserPreferences.POICategory category : UserPreferences.POICategory.values()) {
                // Skip already checked and popularity-based
                if (category == UserPreferences.POICategory.MUSEUM ||
                        category == UserPreferences.POICategory.ZOO ||
                        category == UserPreferences.POICategory.AQUARIUM ||
                        category == UserPreferences.POICategory.OBSERVATION_DECK ||
                        category == UserPreferences.POICategory.LANDMARK ||
                        category == UserPreferences.POICategory.HIDDEN_GEM ||
                        category == UserPreferences.POICategory.TRENDING) {
                    continue;
                }

                if (category.matchesGoogleType(type)) {
                    return category;
                }
            }
        }

        // Check generic types
        if (types.contains("landmark") || types.contains("tourist_attraction")) {
            return UserPreferences.POICategory.LANDMARK;
        }

        // Only if no specific type found, check popularity-based
        if (isHiddenGem()) return UserPreferences.POICategory.HIDDEN_GEM;
        if (isTrending()) return UserPreferences.POICategory.TRENDING;

        // Fallback
        return UserPreferences.POICategory.LANDMARK;
    }

    public List<UserPreferences.POICategory> getAllCategories() {
        List<UserPreferences.POICategory> categories = new ArrayList<>();

        if (isHiddenGem()) categories.add(UserPreferences.POICategory.HIDDEN_GEM);
        if (isTrending()) categories.add(UserPreferences.POICategory.TRENDING);

        for (String type : types) {
            for (UserPreferences.POICategory category : UserPreferences.POICategory.values()) {
                if (category.matchesGoogleType(type) && !categories.contains(category)) {
                    categories.add(category);
                }
            }
        }

        return categories;
    }

    //Check if this point should be EXCLUDED based on user preferences
    // If ANY of the point's categories is avoided, exclude it
    public boolean shouldBeExcluded(UserPreferences preferences) {
        List<UserPreferences.POICategory> allCategories = getAllCategories();

        for (UserPreferences.POICategory category : allCategories) {
            if (preferences.getCategoryWeight(category) == 0.0) {
                return true;
            }
        }

        return false;
    }

    private boolean isHiddenGem() {
        return reviewCount >= 100 && reviewCount <= 2000 && rating >= 4.5;
    }

    private boolean isTrending() {
        return reviewCount > 10000;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScenicPoint)) return false;
        ScenicPoint other = (ScenicPoint) o;
        return Math.abs(latitude - other.latitude) < 1e-6 &&
                Math.abs(longitude - other.longitude) < 1e-6 &&
                Objects.equals(name, other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name,
                Math.round(latitude * 1e6),
                Math.round(longitude * 1e6)
        );
    }
}
