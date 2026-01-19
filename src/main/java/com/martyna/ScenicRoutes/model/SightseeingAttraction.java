package com.martyna.ScenicRoutes.model;

import java.util.List;

// wrapper for ScenicPoint with sightseeing-specific data - for the second mode
public class SightseeingAttraction {
    private final ScenicPoint poi;
    private int importanceScore;

    public SightseeingAttraction(ScenicPoint poi) {
        this.poi = poi;
        this.importanceScore = 0;
    }

    // all ScenicPoint methods
    public String getName() {
        return poi.getName();
    }

    public double getLatitude() {
        return poi.getLatitude();
    }

    public double getLongitude() {
        return poi.getLongitude();
    }

    public double getScore() {
        return poi.getScore();
    }

    public List<String> getTypes() {
        return poi.getTypes();
    }

    public int getReviewCount() {
        return poi.getReviewCount();
    }

    public double getRating() {
        return poi.getRating();
    }

    public UserPreferences.POICategory getPrimaryCategory() {
        return poi.getPrimaryCategory();
    }

    // Sightseeing-specific
    public int getVisitDuration() {
        return com.martyna.ScenicRoutes.service.SmartVisitDurationHelper
                .getSmartVisitMinutes(poi, getPrimaryCategory());
    }

    public int getImportanceScore() {
        return importanceScore;
    }

    public String getPhotoUrl() {
        return poi.getPhotoUrl();
    }

    public void setImportanceScore(int score) {
        this.importanceScore = score;
    }

    public ScenicPoint getWrappedPOI() {
        return poi;
    }
}
