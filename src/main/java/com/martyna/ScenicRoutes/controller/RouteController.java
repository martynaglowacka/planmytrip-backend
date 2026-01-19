package com.martyna.ScenicRoutes.controller;

import com.martyna.ScenicRoutes.model.ScenicRoute;
import com.martyna.ScenicRoutes.model.SightseeingRequest;
import com.martyna.ScenicRoutes.model.SightseeingSchedule;
import com.martyna.ScenicRoutes.model.UserPreferences;
import com.martyna.ScenicRoutes.model.UserPreferences.RouteShape;
import com.martyna.ScenicRoutes.service.OptimizedRouteService;
import com.martyna.ScenicRoutes.service.SightseeingSchedulerService;
import com.martyna.ScenicRoutes.service.WalkingTimeCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
public class RouteController {

    private final OptimizedRouteService optimizedRouteService;
    private final WalkingTimeCache cache;

    @Autowired
    private SightseeingSchedulerService sightseeingService;

    public RouteController(
            OptimizedRouteService optimizedRouteService,
            WalkingTimeCache cache
    ) {
        this.optimizedRouteService = optimizedRouteService;
        this.cache = cache;
    }

    // Returns optimized route with user preferences
    @PostMapping("/api/routes/optimized")
    public ScenicRoute getOptimizedRoute(@RequestBody RouteRequest request) {
        UserPreferences preferences = new UserPreferences();

        // Set route shape
        if (request.routeShape != null) {
            try {
                preferences.setRouteShape(RouteShape.valueOf(request.routeShape.toUpperCase()));
            } catch (IllegalArgumentException e) {
                preferences.setRouteShape(RouteShape.LOOP);
            }
        }

        // Set end point if provided
        if (request.endLat != null && request.endLng != null) {
            preferences.setEndLat(request.endLat);
            preferences.setEndLng(request.endLng);
        }

        // Set category preferences
        if (request.preferences != null) {
            for (Map.Entry<String, Double> entry : request.preferences.entrySet()) {
                try {
                    UserPreferences.POICategory category = UserPreferences.POICategory.valueOf(entry.getKey().toUpperCase());
                    preferences.setCategoryWeight(category, entry.getValue());
                } catch (IllegalArgumentException e) {
                    // Skip unknown categories
                }
            }
        }

        return optimizedRouteService.generateOptimizedRoute(
                request.startLat,
                request.startLng,
                request.minutes,
                preferences
        );
    }

    //Sightseeing scheduler for multi-hour schedules with time windows
    @PostMapping("/api/routes/sightseeing")
    public SightseeingSchedule generateSightseeingDay(@RequestBody SightseeingRequest request) {
        return sightseeingService.generateSchedule(
                request.getStartLat(),
                request.getStartLng(),
                request.getStartTime(),
                request.getEndTime(),
                request.getPreferences(),
                request.isIncludeLunchBreak()
        );
    }

    @GetMapping("/api/cache/stats")
    public WalkingTimeCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    //Request object for optimized routes
    public static class RouteRequest {
        public double startLat;
        public double startLng;
        public int minutes;
        public String routeShape;  // "loop", "one_way", "point_to_point"
        public Double endLat;
        public Double endLng;
        public Map<String, Double> preferences;  // e.g., {"park": 1.5, "museum": 0.8}
    }
}
