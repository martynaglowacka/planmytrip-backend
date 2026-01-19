package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import com.martyna.ScenicRoutes.model.ScenicRoute;
import com.martyna.ScenicRoutes.model.UserPreferences;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class AStarRouteService {

    private final WalkingTimeCache cache;

    public AStarRouteService(WalkingTimeCache cache) {
        this.cache = cache;
    }

    public ScenicRoute findOptimalPointToPointRoute(
            double startLat,
            double startLng,
            double endLat,
            double endLng,
            List<ScenicPoint> pois,
            int timeLimit,
            UserPreferences preferences,
            List<ScenicPoint> guaranteedPois
    ) {
        // Limit to top 20 POIs by score
        List<ScenicPoint> topPOIs = new ArrayList<>(guaranteedPois);
        pois.stream()
                .filter(p -> !guaranteedPois.contains(p))
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(20 - guaranteedPois.size())
                .forEach(topPOIs::add);

        // Create nodes
        Node startNode = new Node("START", startLat, startLng, 0);
        Node endNode = new Node("END", endLat, endLng, 0);

        List<Node> nodes = new ArrayList<>();
        nodes.add(startNode);
        for (ScenicPoint poi : topPOIs) {
            double score = poi.getScore() * preferences.getCategoryWeight(poi.getPrimaryCategory());
            nodes.add(new Node(poi.getName(), poi.getLatitude(), poi.getLongitude(), score));
        }
        nodes.add(endNode);

        // Run A* to find best path
        PathResult result = aStar(startNode, endNode, nodes, timeLimit);

        if (result == null || result.path.size() < 2)
            return new ScenicRoute(new ArrayList<>(), 0, 0, "");

        // Extract pois from path
        List<ScenicPoint> routePOIs = new ArrayList<>();
        double totalScore = 0;

        for (int i = 1; i < result.path.size() - 1; i++) {
            Node node = result.path.get(i);
            ScenicPoint poi = findPOI(topPOIs, node);
            if (poi != null) {
                routePOIs.add(poi);
                totalScore += poi.getScore();
            }
        }

        // Only call API once for final polyline
        String polyline = cache.getWalkingPolylinePointToPoint(
                startLat, startLng, endLat, endLng, routePOIs
        );

        // Get actual time from google routes
        int actualTime = calculateActualTime(startLat, startLng, endLat, endLng, routePOIs);

        return new ScenicRoute(routePOIs, totalScore, actualTime, polyline);
    }

    private PathResult aStar(
            Node start,
            Node end,
            List<Node> allNodes,
            int timeLimit
    ) {
        // Priority queue: prioritize by estimated total time
        PriorityQueue<State> openSet = new PriorityQueue<>((a, b) -> {
            // Primary: lower estimated total time
            int timeCompare = Integer.compare(
                    a.estimatedTotalTime(end),
                    b.estimatedTotalTime(end)
            );
            if (timeCompare != 0) return timeCompare;

            // Secondary: More pois better
            int poiDiff = b.poiCount() - a.poiCount();
            if (poiDiff != 0) return poiDiff;

            // Higher score
            return Double.compare(b.totalScore, a.totalScore);
        });

        Map<String, State> bestState = new HashMap<>();
        State startState = new State(start, 0, 0, Collections.singletonList(start), new HashSet<>());
        openSet.offer(startState);

        State bestEndState = null;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < 100000) {
            iterations++;
            State current = openSet.poll();

            if (current.node.equals(end)) {
                // Only accept paths within time limit
                if (current.totalTime <= timeLimit) {
                    if (bestEndState == null || isBetter(current, bestEndState)) {
                        bestEndState = current;
                    }
                }
                continue;
            }

            // check if we've visited this state
            String key = stateKey(current.node, current.poiCount());
            if (bestState.containsKey(key)) continue;
            bestState.put(key, current);

            // Explore neighbors
            for (Node neighbor : allNodes) {
                if (neighbor.equals(current.node)) continue;

                String neighborKey = neighbor.name + "|" + neighbor.lat + "|" + neighbor.lng;
                if (current.visitedPOIs.contains(neighborKey) && !neighbor.equals(end))
                    continue;

                // Estimate travel time using straight-line distance
                int travelTime = estimateTravelTime(current.node, neighbor);
                int visitTime = neighbor.equals(end) ? 0 : 5;
                int newTime = current.totalTime + travelTime + visitTime;

                // remaining time and reject if over limit
                int estimatedRemaining = estimateTravelTime(neighbor, end);
                if (newTime + estimatedRemaining > timeLimit) continue;

                double newScore = current.totalScore + neighbor.score;
                List<Node> newPath = new ArrayList<>(current.path);
                newPath.add(neighbor);

                Set<String> newVisited = new HashSet<>(current.visitedPOIs);
                if (!neighbor.equals(end)) newVisited.add(neighborKey);

                State newState = new State(neighbor, newTime, newScore, newPath, newVisited);

                // Always add if not visited yet
                String newKey = stateKey(neighbor, newState.poiCount());
                if (!bestState.containsKey(newKey)) openSet.offer(newState);
            }
        }


        return bestEndState != null
                ? new PathResult(bestEndState.path, bestEndState.totalTime, bestEndState.totalScore)
                : null;
    }


     // Average walking speed: 5 km/h = 83 m/min
    private int estimateTravelTime(Node from, Node to) {
        double distanceMeters = calculateDistance(from.lat, from.lng, to.lat, to.lng);
        // Add 50% for non-straight routes and street navigation
        return (int) Math.ceil(distanceMeters / 83.0 * 1.5);
    }

    private int calculateActualTime(
            double startLat, double startLng,
            double endLat, double endLng,
            List<ScenicPoint> pois
    ) {
        if (pois.isEmpty()) return cache.getWalkingTimeMinutes(startLat, startLng, endLat, endLng);

        int totalTime = 0;
        double currentLat = startLat;
        double currentLng = startLng;

        // Walking time to each POI
        for (ScenicPoint poi : pois) {
            totalTime += cache.getWalkingTimeMinutes(
                    currentLat, currentLng,
                    poi.getLatitude(), poi.getLongitude()
            );
            totalTime += 5; // Visit time
            currentLat = poi.getLatitude();
            currentLng = poi.getLongitude();
        }

        // Final leg to end
        totalTime += cache.getWalkingTimeMinutes(currentLat, currentLng, endLat, endLng);

        return totalTime;
    }

    private boolean isBetter(State a, State b) {
        if (a.poiCount() != b.poiCount()) {
            return a.poiCount() > b.poiCount();
        }
        if (Math.abs(a.totalScore - b.totalScore) > 0.01) {
            return a.totalScore > b.totalScore;
        }
        return a.totalTime < b.totalTime;
    }

    private String stateKey(Node node, int poiCount) {
        return node.name + "|" + node.lat + "|" + node.lng + "|" + poiCount;
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

    private ScenicPoint findPOI(List<ScenicPoint> pois, Node node) {
        for (ScenicPoint poi : pois) {
            if (poi.getName().equals(node.name) &&
                    Math.abs(poi.getLatitude() - node.lat) < 0.0001 &&
                    Math.abs(poi.getLongitude() - node.lng) < 0.0001) return poi;
        }
        return null;
    }

    private static class Node {
        String name;
        double lat;
        double lng;
        double score;

        Node(String name, double lat, double lng, double score) {
            this.name = name;
            this.lat = lat;
            this.lng = lng;
            this.score = score;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Node)) return false;
            Node node = (Node) o;
            return Double.compare(node.lat, lat) == 0 &&
                    Double.compare(node.lng, lng) == 0 &&
                    name.equals(node.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, lat, lng);
        }
    }

    private static class State {
        Node node;
        int totalTime;
        double totalScore;
        List<Node> path;
        Set<String> visitedPOIs;

        State(Node node, int totalTime, double totalScore, List<Node> path, Set<String> visitedPOIs) {
            this.node = node;
            this.totalTime = totalTime;
            this.totalScore = totalScore;
            this.path = path;
            this.visitedPOIs = visitedPOIs;
        }

        int poiCount() {
            return visitedPOIs.size();
        }

        int estimatedTotalTime(Node end) {
            double distance = Math.sqrt(
                    Math.pow(end.lat - node.lat, 2) + Math.pow(end.lng - node.lng, 2)
            ) * 111000; // rough meters
            return totalTime + (int) (distance / 83.0 * 1.3);
        }
    }

    private static class PathResult {
        List<Node> path;
        int totalTime;
        double totalScore;

        PathResult(List<Node> path, int totalTime, double totalScore) {
            this.path = path;
            this.totalTime = totalTime;
            this.totalScore = totalScore;
        }
    }
}
