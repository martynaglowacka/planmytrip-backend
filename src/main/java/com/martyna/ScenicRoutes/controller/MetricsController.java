
// MetricsController.java
package com.martyna.ScenicRoutes.controller;

import com.martyna.ScenicRoutes.service.MetricsService;
import com.martyna.ScenicRoutes.service.WalkingTimeCache;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final WalkingTimeCache cache;

    public MetricsController(MetricsService metricsService, WalkingTimeCache cache) {
        this.metricsService = metricsService;
        this.cache = cache;
    }

    @GetMapping
    public MetricsService.MetricsSummary getMetrics() {
        return metricsService.getMetrics();
    }

    @GetMapping("/cache")
    public WalkingTimeCache.CacheStats getCacheStats() {
        return cache.getStats();
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("applicationMetrics", metricsService.getMetrics());
        dashboard.put("cacheMetrics", cache.getStats());
        return dashboard;
    }

    @PostMapping("/reset")
    public Map<String, String> resetMetrics() {
        metricsService.reset();
        return Map.of("status", "Metrics reset successfully");
    }
}
