// MetricsService.java
package com.martyna.ScenicRoutes.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    // Request metrics
    private final AtomicInteger total = new AtomicInteger(0);
    private final AtomicInteger successful = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);

    // Route type metrics
    private final Map<String, AtomicInteger> routeTypeCount = new ConcurrentHashMap<>();

    // Category preferences tracking
    private final Map<String, AtomicInteger> categoryBoostCount = new ConcurrentHashMap<>();

    // Performance metrics
    private final AtomicLong totalGenerationTimeMs = new AtomicLong(0);
    private final List<Long> recentGenerationTimes = Collections.synchronizedList(new ArrayList<>());
    private static final int MAX_RECENT_TIMES = 100;

    // Algorithm metrics
    private final Map<String, AtomicInteger> algorithmUsageCount = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> algorithmTotalTimeMs = new ConcurrentHashMap<>();

    // Error tracking
    private final Map<String, AtomicInteger> errorCount = new ConcurrentHashMap<>();

    // POI metrics
    private final AtomicInteger totalPOIsReturned = new AtomicInteger(0);
    private final AtomicInteger totalRoutesGenerated = new AtomicInteger(0);

    public MetricsService() {
        // Initialize route types
        routeTypeCount.put("LOOP", new AtomicInteger(0));
        routeTypeCount.put("ONE_WAY", new AtomicInteger(0));
        routeTypeCount.put("POINT_TO_POINT", new AtomicInteger(0));

        // Initialize algorithms
        algorithmUsageCount.put("TWO_POINT_LOOP", new AtomicInteger(0));
        algorithmUsageCount.put("GREEDY_ONE_WAY", new AtomicInteger(0));
        algorithmUsageCount.put("ASTAR_P2P", new AtomicInteger(0));

        algorithmTotalTimeMs.put("TWO_POINT_LOOP", new AtomicLong(0));
        algorithmTotalTimeMs.put("GREEDY_ONE_WAY", new AtomicLong(0));
        algorithmTotalTimeMs.put("ASTAR_P2P", new AtomicLong(0));
    }

    // Record request
    public void recordRequest() {
        total.incrementAndGet();
    }

    public void recordSuccess() {
        successful.incrementAndGet();
    }

    public void recordFailure(String errorCode) {
        failed.incrementAndGet();
        errorCount.computeIfAbsent(errorCode, k -> new AtomicInteger(0)).incrementAndGet();
    }

    // Record route generation
    public void recordRouteGeneration(String routeType, String algorithm, long durationMs, int poisCount) {
        totalRoutesGenerated.incrementAndGet();
        totalPOIsReturned.addAndGet(poisCount);

        routeTypeCount.computeIfAbsent(routeType, k -> new AtomicInteger(0)).incrementAndGet();

        // Performance
        totalGenerationTimeMs.addAndGet(durationMs);
        synchronized (recentGenerationTimes) {
            recentGenerationTimes.add(durationMs);
            if (recentGenerationTimes.size() > MAX_RECENT_TIMES)
                recentGenerationTimes.remove(0);
        }

        // Algorithm
        algorithmUsageCount.get(algorithm).incrementAndGet();
        algorithmTotalTimeMs.get(algorithm).addAndGet(durationMs);
    }

    // Record category preferences
    public void recordCategoryBoost(String category) {
        categoryBoostCount.computeIfAbsent(category, k -> new AtomicInteger(0)).incrementAndGet();
    }

    // Get comprehensive metrics
    public MetricsSummary getMetrics() {
        MetricsSummary summary = new MetricsSummary();

        // Request metrics
        summary.totalRequests = total.get();
        summary.successfulRequests = successful.get();
        summary.failedRequests = failed.get();
        summary.successRate = total.get() > 0
                ? (successful.get() * 100.0 / total.get())
                : 0;

        // route metrics
        summary.routeTypeBreakdown = new HashMap<>();
        routeTypeCount.forEach((type, count) ->
                summary.routeTypeBreakdown.put(type, count.get()));

        // Category preferences
        summary.popularCategories = new HashMap<>();
        categoryBoostCount.forEach((cat, count) ->
                summary.popularCategories.put(cat, count.get()));

        // Performance
        summary.totalRoutesGenerated = totalRoutesGenerated.get();
        summary.totalPOIsReturned = totalPOIsReturned.get();
        summary.avgPOIsPerRoute = totalRoutesGenerated.get() > 0
                ? (totalPOIsReturned.get() * 1.0 / totalRoutesGenerated.get())
                : 0;

        long totalTime = totalGenerationTimeMs.get();
        summary.avgGenerationTimeMs = totalRoutesGenerated.get() > 0
                ? (totalTime / totalRoutesGenerated.get())
                : 0;

        // recent performance
        synchronized (recentGenerationTimes) {
            if (!recentGenerationTimes.isEmpty()) {
                summary.recentAvgGenerationTimeMs = recentGenerationTimes.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);
                summary.p95GenerationTimeMs = calculatePercentile(recentGenerationTimes, 95);
                summary.p99GenerationTimeMs = calculatePercentile(recentGenerationTimes, 99);
            }
        }

        // Algorithm metrics
        summary.algorithmUsage = new HashMap<>();
        summary.algorithmAvgTimeMs = new HashMap<>();

        algorithmUsageCount.forEach((algo, count) -> {
            int usage = count.get();
            summary.algorithmUsage.put(algo, usage);

            if (usage > 0) {
                long avgTime = algorithmTotalTimeMs.get(algo).get() / usage;
                summary.algorithmAvgTimeMs.put(algo, avgTime);
            }
        });

        // Errors
        summary.errorBreakdown = new HashMap<>();
        errorCount.forEach((code, count) ->
                summary.errorBreakdown.put(code, count.get()));

        summary.timestamp = LocalDateTime.now();

        return summary;
    }

    private long calculatePercentile(List<Long> values, int percentile) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public void reset() {
        total.set(0);
        successful.set(0);
        failed.set(0);
        totalPOIsReturned.set(0);
        totalRoutesGenerated.set(0);
        totalGenerationTimeMs.set(0);
        recentGenerationTimes.clear();
        routeTypeCount.values().forEach(v -> v.set(0));
        categoryBoostCount.clear();
        algorithmUsageCount.values().forEach(v -> v.set(0));
        algorithmTotalTimeMs.values().forEach(v -> v.set(0));
        errorCount.clear();
    }

    // Metrics summary DTO
    public static class MetricsSummary {
        public int totalRequests;
        public int successfulRequests;
        public int failedRequests;
        public double successRate;

        public int totalRoutesGenerated;
        public int totalPOIsReturned;
        public double avgPOIsPerRoute;

        public long avgGenerationTimeMs;
        public double recentAvgGenerationTimeMs;
        public long p95GenerationTimeMs;
        public long p99GenerationTimeMs;

        public Map<String, Integer> routeTypeBreakdown;
        public Map<String, Integer> popularCategories;
        public Map<String, Integer> algorithmUsage;
        public Map<String, Long> algorithmAvgTimeMs;
        public Map<String, Integer> errorBreakdown;

        public LocalDateTime timestamp;
    }
}

