package com.martyna.ScenicRoutes.model;

public class SightseeingRequest {
    private double startLat;
    private double startLng;
    private String startTime;
    private String endTime;
    private UserPreferences preferences;
    private boolean includeLunchBreak;

    // Constructors
    public SightseeingRequest() {}

    public SightseeingRequest(double startLat, double startLng, String startTime,
                              String endTime, UserPreferences preferences, boolean includeLunchBreak) {
        this.startLat = startLat;
        this.startLng = startLng;
        this.startTime = startTime;
        this.endTime = endTime;
        this.preferences = preferences;
        this.includeLunchBreak = includeLunchBreak;
    }

    // Getters and setters
    public double getStartLat() {
        return startLat;
    }

    public void setStartLat(double lat) {
        this.startLat = lat;
    }

    public double getStartLng() {
        return startLng;
    }

    public void setStartLng(double lng) {
        this.startLng = lng;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String time) {
        this.startTime = time;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String time) {
        this.endTime = time;
    }

    public UserPreferences getPreferences() {
        return preferences;
    }

    public void setPreferences(UserPreferences prefs) {
        this.preferences = prefs;
    }

    public boolean isIncludeLunchBreak() {
        return includeLunchBreak;
    }

    public void setIncludeLunchBreak(boolean include) {
        this.includeLunchBreak = include;
    }
}
