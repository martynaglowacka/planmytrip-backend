package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.exception.RouteGenerationException;
import com.martyna.ScenicRoutes.model.ScenicPoint;
import com.martyna.ScenicRoutes.model.ScenicRoute;
import com.martyna.ScenicRoutes.model.UserPreferences;
import com.martyna.ScenicRoutes.model.UserPreferences.RouteShape;
import org.springframework.stereotype.Service;

import java.util.*;
// Core route-planning engine responsible for generating optimized sightseeing routes
// under real-world constraints such as time limits, user preferences, and route shape.
@Service
public class OptimizedRouteService {

    private final GooglePlacesService googlePlacesService;
    private final GoogleRoutesService routesService;
    private final TwoPointLoopService twoPointLoopService;
    private final AStarRouteService aStarRouteService;
    private final WalkingTimeCache cache;
    private final MetricsService metricsService;

    public OptimizedRouteService(
            GooglePlacesService googlePlacesService,
            GoogleRoutesService routesService,
            TwoPointLoopService twoPointLoopService,
            AStarRouteService aStarRouteService,
            WalkingTimeCache cache,
            MetricsService metricsService
    ) {
        this.googlePlacesService = googlePlacesService;
        this.routesService = routesService;
        this.twoPointLoopService = twoPointLoopService;
        this.aStarRouteService = aStarRouteService;
        this.cache = cache;
        this.metricsService = metricsService;
    }

    public ScenicRoute generateOptimizedRoute(
            double startLat,
            double startLng,
            int minutes,
            UserPreferences preferences
    ) {
        long startTime = System.currentTimeMillis();
        metricsService.recordRequest();

        try {
            // Validate inputs
            validateCoordinates(startLat, startLng, "Start");
            validateTimeLimit(minutes);

            if (preferences.getRouteShape() == RouteShape.POINT_TO_POINT) {
                if (!preferences.hasEndPoint()) {
                    throw new RouteGenerationException(
                            "End point required for point-to-point routes",
                            "MISSING_END_POINT"
                    );
                }
                validateCoordinates(preferences.getEndLat(), preferences.getEndLng(), "End");
            }


            // Track boosted categories
            preferences.getBoostedCategories().forEach((cat, weight) ->
                    metricsService.recordCategoryBoost(cat.name()));

            List<ScenicPoint> originalPois = googlePlacesService.getNearbyPOIs(startLat, startLng);

            List<ScenicPoint> scoredPois = applyPreferenceScoring(originalPois, preferences);


            if (scoredPois.isEmpty()) {
                throw new RouteGenerationException(
                        "No suitable points of interest found with your preferences",
                        "NO_SUITABLE_POIS"
                );
            }

            ScenicRoute route;
            String algorithm;

            if (preferences.getRouteShape() == UserPreferences.RouteShape.LOOP) {
                algorithm = "TWO_POINT_LOOP";

                route = twoPointLoopService.generateTwoPointLoop(
                        startLat, startLng, scoredPois, minutes, preferences
                );
            } else if (preferences.getRouteShape() == RouteShape.POINT_TO_POINT && preferences.hasEndPoint()) {
                algorithm = "ASTAR_P2P";

                route = aStarRouteService.findOptimalPointToPointRoute(
                        startLat, startLng,
                        preferences.getEndLat(), preferences.getEndLng(),
                        scoredPois,
                        minutes,
                        preferences,
                        new ArrayList<>()
                );
            } else {
                algorithm = "GREEDY_ONE_WAY";

                route = buildDensityAwareRoute(
                        startLat, startLng,
                        scoredPois,
                        minutes
                );
            }


            long duration = System.currentTimeMillis() - startTime;
            metricsService.recordSuccess();
            metricsService.recordRouteGeneration(
                    preferences.getRouteShape().name(),
                    algorithm,
                    duration,
                    route.getPoints().size()
            );

            return route;

        } catch (RouteGenerationException e) {
            metricsService.recordFailure(e.getErrorCode());
            throw e;
        } catch (Exception e) {
            metricsService.recordFailure("UNEXPECTED_ERROR");
            throw new RouteGenerationException(
                    "Unexpected error generating route",
                    "UNEXPECTED_ERROR",
                    e
            );
        }
    }

    private List<ScenicPoint> applyPreferenceScoring(
            List<ScenicPoint> pois,
            UserPreferences preferences
    ) {
        if (!preferences.hasCustomPreferences()) {
            return pois;
        }

        List<ScenicPoint> scored = new ArrayList<>();
        Map<UserPreferences.POICategory, Double> explicitlyBoosted = preferences.getCategoryWeights();
        for (ScenicPoint poi : pois) {
            List<UserPreferences.POICategory> categories = poi.getAllCategories();


        }
        for (ScenicPoint poi : pois) {
            List<UserPreferences.POICategory> categories = poi.getAllCategories();

            // First, check if any category is excluded
            boolean isExcluded = false;
            for (UserPreferences.POICategory category : categories) {
                if (explicitlyBoosted.containsKey(category) &&
                        explicitlyBoosted.get(category) == 0.0) {
                    isExcluded = true;
                    break;
                }
            }

            // Skip this POI entirely if it contains any excluded category
            if (isExcluded) {
                continue;
            }

            // Now find the highest boost weight for non-excluded categories
            double maxWeight = 0.0;
            boolean hasMatchingCategory = false;

            for (UserPreferences.POICategory category : categories) {
                if (explicitlyBoosted.containsKey(category)) {
                    hasMatchingCategory = true;
                    double weight = explicitlyBoosted.get(category);
                    if (weight > maxWeight) {
                        maxWeight = weight;
                    }
                }
            }

            // If no matching category found in preferences, treat as neutral
            if (!hasMatchingCategory) {
                maxWeight = 1.0;
            }

            double weightedScore = poi.getScore() * maxWeight;
            ScenicPoint weighted = new ScenicPoint(
                    poi.getName(),
                    poi.getLatitude(),
                    poi.getLongitude(),
                    weightedScore,
                    poi.getTypes(),
                    poi.getReviewCount()
            );
            weighted.setPhotoUrl(poi.getPhotoUrl());
            scored.add(weighted);
        }

        return scored;
    }

    private ScenicRoute buildDensityAwareRoute(
            double startLat,
            double startLng,
            List<ScenicPoint> pois,
            int minutes
    ) {

        List<ScenicPoint> selectedPois = selectPOIsGreedy(startLat, startLng, pois, minutes);
        List<ScenicPoint> orderedRoute = reorderPOIsNearestNeighbor(startLat, startLng, selectedPois);
        List<ScenicPoint> finalRoute = trimRouteToTimeBudget(startLat, startLng, orderedRoute, minutes);

        int totalWalkingTime = 0;
        double totalScore = 0, currentLat = startLat, currentLng = startLng;

        for (ScenicPoint poi : finalRoute) {
            int travelTime = cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    poi.getLatitude(), poi.getLongitude()
            );
            totalWalkingTime += travelTime;
            totalScore += poi.getScore();
            currentLat = poi.getLatitude();
            currentLng = poi.getLongitude();
        }

        int totalTimeUsed = totalWalkingTime + (finalRoute.size() * 5);

        String polyline = "";
        if (!finalRoute.isEmpty()) {
            polyline = cache.getWalkingPolylineWithWaypoints(startLat, startLng, finalRoute);
        }

        return new ScenicRoute(finalRoute, totalScore, totalTimeUsed, polyline);
    }

    private List<ScenicPoint> selectPOIsGreedy(
            double startLat,
            double startLng,
            List<ScenicPoint> pois,
            int minutes
    ) {
        List<ScenicPoint> selected = new ArrayList<>();
        Set<ScenicPoint> used = new HashSet<>();

        int maxPOIs = Math.min((minutes / 8) + 3, 20);

        for (int i = 0; i < maxPOIs && used.size() < pois.size(); i++) {
            ScenicPoint best = null;
            double bestScore = -Double.MAX_VALUE;

            for (ScenicPoint poi : pois) {
                if (used.contains(poi)) continue;

                double densityBonus = calculateDensityBonus(poi, pois, used);

                double distanceFromStart = calculateDistance(
                        startLat, startLng, poi.getLatitude(), poi.getLongitude()
                );

                double proximityBonus = 0;
                if (i < 3 && distanceFromStart < 1000) proximityBonus = 200;


                double score = poi.getScore() + densityBonus + proximityBonus;

                if (score > bestScore) {
                    bestScore = score;
                    best = poi;
                }
            }

            if (best == null) break;

            selected.add(best);
            used.add(best);

            double dist = calculateDistance(startLat, startLng, best.getLatitude(), best.getLongitude());
        }

        return selected;
    }
    // makes sure the points are in order, minimizing backtracking
    private List<ScenicPoint> reorderPOIsNearestNeighbor(
            double startLat,
            double startLng,
            List<ScenicPoint> pois
    ) {
        if (pois.isEmpty()) return new ArrayList<>();

        List<ScenicPoint> ordered = new ArrayList<>();
        Set<ScenicPoint> remaining = new HashSet<>(pois);

        double currentLat = startLat;
        double currentLng = startLng;

        while (!remaining.isEmpty()) {
            ScenicPoint nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (ScenicPoint poi : remaining) {
                double distance = calculateDistance(
                        currentLat, currentLng,
                        poi.getLatitude(), poi.getLongitude()
                );

                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = poi;
                }
            }

            if (nearest != null) {
                ordered.add(nearest);
                remaining.remove(nearest);
                currentLat = nearest.getLatitude();
                currentLng = nearest.getLongitude();
            }
        }

        return ordered;
    }
    // if we get more pois fill the route with them until time runs out
    private List<ScenicPoint> trimRouteToTimeBudget(
            double startLat,
            double startLng,
            List<ScenicPoint> orderedPois,
            int maxMinutes
    ) {
        List<ScenicPoint> trimmed = new ArrayList<>();
        double currentLat = startLat;
        double currentLng = startLng;
        int timeUsed = 0;

        for (ScenicPoint poi : orderedPois) {
            int travelTime = cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    poi.getLatitude(), poi.getLongitude()
            );

            int timeWithThisPOI = timeUsed + travelTime + 5;

            if (timeWithThisPOI <= maxMinutes) {
                trimmed.add(poi);
                timeUsed = timeWithThisPOI;
                currentLat = poi.getLatitude();
                currentLng = poi.getLongitude();
            }
        }

        return trimmed;
    }

    private double calculateDensityBonus(
            ScenicPoint poi,
            List<ScenicPoint> allPois,
            Set<ScenicPoint> used
    ) {
        double densityScore = 0;
        int nearbyCount = 0;

        for (ScenicPoint other : allPois) {
            if (other == poi || used.contains(other)) continue;

            double distance = calculateDistance(
                    poi.getLatitude(), poi.getLongitude(),
                    other.getLatitude(), other.getLongitude()
            );

            if (distance < 300) {
                nearbyCount++;
                densityScore += other.getScore() * 0.5;
            }
        }


        return densityScore;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private void validateCoordinates(double lat, double lng, String pointName) {
        if (lat < -90 || lat > 90) {
            throw new RouteGenerationException(
                    pointName + " latitude must be between -90 and 90",
                    "INVALID_LATITUDE"
            );
        }
        if (lng < -180 || lng > 180) {
            throw new RouteGenerationException(
                    pointName + " longitude must be between -180 and 180",
                    "INVALID_LONGITUDE"
            );
        }
    }

    private void validateTimeLimit(int minutes) {
        if (minutes < 10) {
            throw new RouteGenerationException(
                    "Time limit must be at least 10 minutes",
                    "TIME_LIMIT_TOO_SHORT"
            );
        }
        if (minutes > 480) {
            throw new RouteGenerationException(
                    "Time limit cannot exceed 8 hours (480 minutes)",
                    "TIME_LIMIT_TOO_LONG"
            );
        }
    }
}
