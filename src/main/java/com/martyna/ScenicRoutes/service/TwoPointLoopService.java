package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import com.martyna.ScenicRoutes.model.ScenicRoute;
import com.martyna.ScenicRoutes.model.UserPreferences;
import org.springframework.stereotype.Service;

import java.util.*;

//Generates optimized loop routes by iteratively extending outward
// and finding optimal return paths within time budget
@Service
public class TwoPointLoopService {

    private final GoogleRoutesService routesService;
    private final WalkingTimeCache cache;

    public TwoPointLoopService(GoogleRoutesService routesService, WalkingTimeCache cache) {
        this.routesService = routesService;
        this.cache = cache;
    }

    public ScenicRoute generateTwoPointLoop(
            double startLat,
            double startLng,
            List<ScenicPoint> pois,
            int minutes,
            UserPreferences preferences
    ) {
        Set<ScenicPoint> allPoisSet = new HashSet<>(pois);
        List<ScenicPoint> allPois = new ArrayList<>(allPoisSet);

        List<ScenicPoint> outwardRoute = new ArrayList<>();
        Set<ScenicPoint> usedOnOutward = new HashSet<>();

        double currentLat = startLat;
        double currentLng = startLng;
        int outwardTime = 0;

        ScenicRoute bestValidLoop = null;

        while (true) {

            ScenicPoint nextOutward = findBestOutwardPOI(
                    currentLat, currentLng,
                    allPois,
                    usedOnOutward,
                    preferences
            );

            if (nextOutward == null) break;

            int travelTime = cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    nextOutward.getLatitude(), nextOutward.getLongitude()
            );
            int visitTime = 5;

            // add to outward route
            outwardRoute.add(nextOutward);
            usedOnOutward.add(nextOutward);
            int newOutwardTime = outwardTime + travelTime + visitTime;

            // Find optimal return route from this position
            ScenicRoute returnRoute = findOptimalReturnRoute(
                    nextOutward.getLatitude(), nextOutward.getLongitude(),
                    startLat, startLng,
                    allPois,
                    usedOnOutward,
                    minutes - newOutwardTime,
                    preferences
            );

            int totalTime = newOutwardTime + returnRoute.getTotalTime();

            if (totalTime <= minutes) {
                // Valid loop - combine outward and return
                List<ScenicPoint> fullRoute = new ArrayList<>();
                fullRoute.addAll(outwardRoute);
                fullRoute.addAll(returnRoute.getPoints());

                double totalScore = fullRoute.stream()
                        .mapToDouble(ScenicPoint::getScore)
                        .sum();

                String polyline = generateSafePolyline(startLat, startLng, fullRoute);

                bestValidLoop = new ScenicRoute(fullRoute, totalScore, totalTime, polyline);

                currentLat = nextOutward.getLatitude();
                currentLng = nextOutward.getLongitude();
                outwardTime = newOutwardTime;

            } else {
                // Doesn't fit - revert and stop
                outwardRoute.remove(outwardRoute.size() - 1);
                usedOnOutward.remove(nextOutward);
                break;
            }
        }

        if (bestValidLoop == null) return new ScenicRoute(new ArrayList<>(), 0, 0, "");

        return bestValidLoop;
    }

    //Finds best next POI for outward route based on quality-to-time efficiency
    private ScenicPoint findBestOutwardPOI(
            double currentLat,
            double currentLng,
            List<ScenicPoint> allPois,
            Set<ScenicPoint> used,
            UserPreferences preferences
    ) {
        ScenicPoint best = null;
        double bestScore = -Double.MAX_VALUE;

        for (ScenicPoint poi : allPois) {
            if (used.contains(poi)) continue;

            int travelTime = cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    poi.getLatitude(), poi.getLongitude()
            );

            // Skip unreachable or too-distant POIs (>30 min)
            if (travelTime == Integer.MAX_VALUE || travelTime > 30) continue;

            // Apply user preference multipliers, use highest if multiple categories
            List<UserPreferences.POICategory> categories = poi.getAllCategories();
            double maxWeight = 1.0;
            for (UserPreferences.POICategory category : categories) {
                double weight = preferences.getCategoryWeight(category);
                if (weight > maxWeight) {
                    maxWeight = weight;
                }
            }

            // Score by quality-to-time efficiency
            double poiQuality = poi.getScore() * maxWeight;
            double efficiency = poiQuality / Math.max(1, travelTime);

            if (efficiency > bestScore) {
                bestScore = efficiency;
                best = poi;
            }
        }

        return best;
    }

    // Finds optimal return route to start, prioritizing POIs that move
    // towards the start
    private ScenicRoute findOptimalReturnRoute(
            double fromLat,
            double fromLng,
            double toLat,
            double toLng,
            List<ScenicPoint> allPois,
            Set<ScenicPoint> usedPois,
            int timeLimit,
            UserPreferences preferences
    ) {

        List<ScenicPoint> returnRoute = new ArrayList<>();
        Set<ScenicPoint> used = new HashSet<>(usedPois);

        double currentLat = fromLat;
        double currentLng = fromLng;
        int timeRemaining = timeLimit;

        // Check if we can reach the start point directly
        int directHomeTime = cache.getWalkingTimeMinutes(fromLat, fromLng, toLat, toLng);
        if (directHomeTime > timeLimit) return new ScenicRoute(returnRoute, 0, 999, "");


        // Greedily add POIs while moving toward the start location, keep 15min buffer
        int iteration = 0;
        while (timeRemaining > directHomeTime + 15) {
            iteration++;
            ScenicPoint best = null;
            double bestScore = -Double.MAX_VALUE;
            int bestTravelTime = 0;
            int bestTimeToHome = 0;

            for (ScenicPoint poi : allPois) {
                if (used.contains(poi)) continue;

                int travelTime = cache.getWalkingTimeMinutes(
                        currentLat, currentLng,
                        poi.getLatitude(), poi.getLongitude()
                );

                if (travelTime == Integer.MAX_VALUE) continue;

                // Check if we can visit this POI and still get back
                int timeToHome = cache.getWalkingTimeMinutes(
                        poi.getLatitude(), poi.getLongitude(),
                        toLat, toLng
                );

                int totalTimeNeeded = travelTime + 10 + timeToHome;

                if (timeRemaining < totalTimeNeeded) {
                    continue;
                }

                // Apply user preference multipliers
                List<UserPreferences.POICategory> categories = poi.getAllCategories();
                double maxWeight = 1.0;
                for (UserPreferences.POICategory category : categories) {
                    double weight = preferences.getCategoryWeight(category);
                    if (weight > maxWeight) {
                        maxWeight = weight;
                    }
                }

                double poiQuality = poi.getScore() * maxWeight;

                // Bonus for pois that move us closer back
                int currentDistToHome = cache.getWalkingTimeMinutes(
                        currentLat, currentLng, toLat, toLng
                );

                double homeProgressBonus = 0;
                if (timeToHome < currentDistToHome) {
                    homeProgressBonus = (currentDistToHome - timeToHome) * 2;
                }

                double totalScore = (poiQuality + homeProgressBonus) / Math.max(1, travelTime);

                if (totalScore > bestScore) {
                    bestScore = totalScore;
                    best = poi;
                    bestTravelTime = travelTime;
                    bestTimeToHome = timeToHome;
                }
            }

            if (best == null) break;

            returnRoute.add(best);
            used.add(best);
            timeRemaining -= (bestTravelTime + 10);

            currentLat = best.getLatitude();
            currentLng = best.getLongitude();
        }

        int actualTime = calculateRouteTime(fromLat, fromLng, returnRoute, toLat, toLng);
        double score = returnRoute.stream().mapToDouble(ScenicPoint::getScore).sum();

        return new ScenicRoute(returnRoute, score, actualTime, "");
    }

    // Calculates total walking time for a route segment including visit times
    private int calculateRouteTime(
            double startLat, double startLng,
            List<ScenicPoint> points,
            double endLat, double endLng
    ) {
        if (points.isEmpty()) {
            return cache.getWalkingTimeMinutes(startLat, startLng, endLat, endLng);
        }

        int time = 0;
        double currentLat = startLat;
        double currentLng = startLng;

        for (ScenicPoint poi : points) {
            time += cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    poi.getLatitude(), poi.getLongitude()
            );
            time += 10; // Visit time
            currentLat = poi.getLatitude();
            currentLng = poi.getLongitude();
        }

        // Final leg to end point
        time += cache.getWalkingTimeMinutes(currentLat, currentLng, endLat, endLng);

        return time;
    }

    private String generateSafePolyline(double startLat, double startLng, List<ScenicPoint> points) {
        try {
            if (points.isEmpty()) return "";
            return cache.getWalkingPolylineForLoop(startLat, startLng, points);
        } catch (Exception e) {
            System.err.println("Error generating polyline: " + e.getMessage());
            return "";
        }
    }
}
