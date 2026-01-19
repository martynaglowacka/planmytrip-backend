package com.martyna.ScenicRoutes.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

// Cache for Google Maps walking time requests
@Service
public class WalkingTimeCache {

    private final GoogleRoutesService routesService;

    // cache for walking times
    private final Map<String, Integer> walkingTimeCache = new ConcurrentHashMap<>();

    // cache for polylines
    private final Map<String, String> polylineCache = new ConcurrentHashMap<>();

    // Statistics
    private int cacheHits = 0;
    private int cacheMisses = 0;
    private int totalRequests = 0;

    public WalkingTimeCache(GoogleRoutesService routesService) {
        this.routesService = routesService;
    }

    // Get walking time with caching
    public int getWalkingTimeMinutes(
            double fromLat, double fromLng,
            double toLat, double toLng
    ) {
        totalRequests++;
        String key = createLocationKey(fromLat, fromLng, toLat, toLng);

        Integer cached = walkingTimeCache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }

        // Cache miss - call API
        cacheMisses++;
        int time = routesService.getWalkingTimeMinutes(fromLat, fromLng, toLat, toLng);
        walkingTimeCache.put(key, time);

        // Logs to track cache effectiveness
        if (totalRequests % 50 == 0) {
            logCacheStats();
        }

        return time;
    }

    //Get polyline for one-way route with caching
    public String getWalkingPolylineWithWaypoints(
            double startLat, double startLng,
            java.util.List<com.martyna.ScenicRoutes.model.ScenicPoint> points
    ) {
        String key = createPolylineKey(startLat, startLng, points, false);

        String cached = polylineCache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }

        // Cache miss - call API
        cacheMisses++;
        String polyline = routesService.getWalkingPolylineWithWaypoints(startLat, startLng, points);
        polylineCache.put(key, polyline);

        return polyline;
    }

    //Get polyline for loop routes with caching
    public String getWalkingPolylineForLoop(
            double startLat, double startLng,
            java.util.List<com.martyna.ScenicRoutes.model.ScenicPoint> points
    ) {
        String key = createPolylineKey(startLat, startLng, points, true);

        String cached = polylineCache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }

        // Cache miss - call API
        cacheMisses++;
        String polyline = routesService.getWalkingPolylineForLoop(startLat, startLng, points);
        polylineCache.put(key, polyline);

        return polyline;
    }

    //Get polyline for point-to-point routes with caching
    public String getWalkingPolylinePointToPoint(
            double startLat, double startLng,
            double endLat, double endLng,
            java.util.List<com.martyna.ScenicRoutes.model.ScenicPoint> points
    ) {
        String key = createPointToPointPolylineKey(startLat, startLng, endLat, endLng, points);

        String cached = polylineCache.get(key);
        if (cached != null) {
            cacheHits++;
            return cached;
        }

        // Cache miss - call API
        cacheMisses++;
        String polyline = routesService.getWalkingPolylinePointToPoint(
                startLat, startLng, endLat, endLng, points
        );
        polylineCache.put(key, polyline);

        return polyline;
    }

    private String createLocationKey(double lat1, double lng1, double lat2, double lng2) {
        return String.format("%.6f,%.6f->%.6f,%.6f", lat1, lng1, lat2, lng2);
    }

    private String createPolylineKey(
            double startLat, double startLng,
            java.util.List<com.martyna.ScenicRoutes.model.ScenicPoint> points,
            boolean isLoop
    ) {
        StringBuilder key = new StringBuilder();
        key.append(String.format("%.6f,%.6f", startLat, startLng));
        key.append(isLoop ? "|LOOP|" : "|WAYPOINTS|");

        for (com.martyna.ScenicRoutes.model.ScenicPoint point : points) {
            key.append(String.format("%.6f,%.6f;", point.getLatitude(), point.getLongitude()));
        }

        return key.toString();
    }

   // Create cache key for point-to-point polyline requests
    private String createPointToPointPolylineKey(
            double startLat, double startLng,
            double endLat, double endLng,
            java.util.List<com.martyna.ScenicRoutes.model.ScenicPoint> points
    ) {
        StringBuilder key = new StringBuilder();
        key.append(String.format("%.6f,%.6f->%.6f,%.6f|P2P|", startLat, startLng, endLat, endLng));

        for (com.martyna.ScenicRoutes.model.ScenicPoint point : points) {
            key.append(String.format("%.6f,%.6f;", point.getLatitude(), point.getLongitude()));
        }

        return key.toString();
    }


    public CacheStats getStats() {
        return new CacheStats(
                totalRequests,
                cacheHits,
                cacheMisses,
                walkingTimeCache.size(),
                polylineCache.size()
        );
    }

    // Log cache performance
    private void logCacheStats() {
        double hitRate = totalRequests > 0
                ? (cacheHits * 100.0 / totalRequests)
                : 0;

    }


    public void clearCache() {
        walkingTimeCache.clear();
        polylineCache.clear();
        cacheHits = 0;
        cacheMisses = 0;
        totalRequests = 0;
    }

    public int getCacheSize() {
        return walkingTimeCache.size() + polylineCache.size();
    }

    // Cache statistics data class
    public static class CacheStats {
        public final int totalRequests;
        public final int hits;
        public final int misses;
        public final int walkingTimeCacheSize;
        public final int polylineCacheSize;

        public CacheStats(int totalRequests, int hits, int misses,
                          int walkingTimeCacheSize, int polylineCacheSize) {
            this.totalRequests = totalRequests;
            this.hits = hits;
            this.misses = misses;
            this.walkingTimeCacheSize = walkingTimeCacheSize;
            this.polylineCacheSize = polylineCacheSize;
        }

        public double getHitRate() {
            return totalRequests > 0 ? (hits * 100.0 / totalRequests) : 0;
        }

        public int getTotalCacheSize() {
            return walkingTimeCacheSize + polylineCacheSize;
        }
    }
}
