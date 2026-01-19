package com.martyna.ScenicRoutes.model;

import java.util.ArrayList;
import java.util.List;

// A scheduled day plan with timed stops and breaks
public class SightseeingSchedule {
    private List<ScheduledStop> stops;
    private List<Break> breaks;
    private int totalMinutes;
    private double totalCost;
    private String polyline;

    public SightseeingSchedule() {
        this.stops = new ArrayList<>();
        this.breaks = new ArrayList<>();
    }

    // Inner class for scheduled stops
    public static class ScheduledStop {
        private SightseeingAttraction attraction;
        private String arrivalTime;
        private String departureTime;
        private int visitMinutes;
        private int travelMinutes;  // from previous stop

        public ScheduledStop() {}

        public ScheduledStop(SightseeingAttraction attraction, String arrivalTime,
                             String departureTime, int visitMinutes, int travelMinutes) {
            this.attraction = attraction;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
            this.visitMinutes = visitMinutes;
            this.travelMinutes = travelMinutes;
        }

        // Getters and setters
        public SightseeingAttraction getAttraction() { return attraction; }
        public void setAttraction(SightseeingAttraction attr) { this.attraction = attr; }

        public String getArrivalTime() { return arrivalTime; }
        public void setArrivalTime(String time) { this.arrivalTime = time; }

        public String getDepartureTime() { return departureTime; }
        public void setDepartureTime(String time) { this.departureTime = time; }

        public int getVisitMinutes() { return visitMinutes; }
        public void setVisitMinutes(int minutes) { this.visitMinutes = minutes; }

        public int getTravelMinutes() { return travelMinutes; }
        public void setTravelMinutes(int minutes) { this.travelMinutes = minutes; }
    }

    // Inner class for breaks
    public static class Break {
        private String startTime;
        private int durationMinutes;
        private String type; // can add other breaks than just lunch in the future
        private String suggestion;

        public Break() {}

        public Break(String startTime, int durationMinutes, String type, String suggestion) {
            this.startTime = startTime;
            this.durationMinutes = durationMinutes;
            this.type = type;
            this.suggestion = suggestion;
        }

        // Getters and setters
        public String getStartTime() { return startTime; }
        public void setStartTime(String time) { this.startTime = time; }

        public int getDurationMinutes() { return durationMinutes; }
        public void setDurationMinutes(int minutes) { this.durationMinutes = minutes; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
    }

    // Main getters and setters
    public List<ScheduledStop> getStops() {
        return stops;
    }

    public void setStops(List<ScheduledStop> stops) {
        this.stops = stops;
    }

    public List<Break> getBreaks() {
        return breaks;
    }

    public void setBreaks(List<Break> breaks) {
        this.breaks = breaks;
    }

    public int getTotalMinutes() {
        return totalMinutes;
    }

    public void setTotalMinutes(int minutes) {
        this.totalMinutes = minutes;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double cost) {
        this.totalCost = cost;
    }

    public String getPolyline() {
        return polyline;
    }

    public void setPolyline(String polyline) {
        this.polyline = polyline;
    }
}
