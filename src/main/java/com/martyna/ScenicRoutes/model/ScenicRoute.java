package com.martyna.ScenicRoutes.model;

import java.util.ArrayList;
import java.util.List;

public class ScenicRoute {
    private final List<ScenicPoint> points;
    private final double totalScore;
    private final int totalTime;
    private final String polyline;
    private List<String> warnings; // New field for warnings

    public ScenicRoute(List<ScenicPoint> points, double totalScore, int totalTime, String polyline) {
        this.points = points;
        this.totalScore = totalScore;
        this.totalTime = totalTime;
        this.polyline = polyline;
        this.warnings = new ArrayList<>();
    }

    public List<ScenicPoint> getPoints() {
        return points;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public String getPolyline() {
        return polyline;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
