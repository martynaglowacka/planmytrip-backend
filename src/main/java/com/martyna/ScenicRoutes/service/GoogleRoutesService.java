package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

// Service for Google Routes API integration - handles walking directions and polylines
@Service
public class GoogleRoutesService {

    @Value("${google.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    // Calculates walking time between two points using Google Routes API
    public int getWalkingTimeMinutes(
            double fromLat, double fromLng,
            double toLat, double toLng
    ) {
        String url = "https://routes.googleapis.com/directions/v2:computeRoutes";

        // Build request body with origin and destination
        JSONObject body = new JSONObject();
        body.put("travelMode", "WALK");
        body.put("optimizeWaypointOrder", false);

        body.put("origin", new JSONObject()
                .put("location", new JSONObject()
                        .put("latLng", new JSONObject()
                                .put("latitude", fromLat)
                                .put("longitude", fromLng))));

        body.put("destination", new JSONObject()
                .put("location", new JSONObject()
                        .put("latLng", new JSONObject()
                                .put("latitude", toLat)
                                .put("longitude", toLng))));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.duration"); // Only request duration field

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        ResponseEntity<String> response =
                restTemplate.postForEntity(url, request, String.class);

        // Parse response and convert seconds to minutes
        JSONObject json = new JSONObject(response.getBody());
        JSONArray routes = json.getJSONArray("routes");

        if (routes.isEmpty()) return Integer.MAX_VALUE;

        String duration = routes.getJSONObject(0).getString("duration");
        return Integer.parseInt(duration.replace("s", "")) / 60;
    }

    // Generates walking route polyline for one-way routes
    // (used for visualizing routes on the map)
    public String getWalkingPolylineWithWaypoints(
            double startLat,
            double startLng,
            List<ScenicPoint> points
    ) {
        String url = "https://routes.googleapis.com/directions/v2:computeRoutes";

        // Add all points except last as intermediates (last becomes destination)
        JSONArray intermediates = new JSONArray();
        for (int i = 0; i < points.size() - 1; i++) {
            ScenicPoint p = points.get(i);
            JSONObject waypoint = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", p.getLatitude())
                                    .put("longitude", p.getLongitude())
                            )
                    );
            intermediates.put(waypoint);
        }

        JSONObject body = new JSONObject()
                .put("travelMode", "WALK")
                .put("origin", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", startLat)
                                        .put("longitude", startLng))))
                .put("destination", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", points.get(points.size() - 1).getLatitude())
                                        .put("longitude", points.get(points.size() - 1).getLongitude()))));

        if (!intermediates.isEmpty()) body.put("intermediates", intermediates);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.polyline.encodedPolyline"); // Only request polyline

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        String response = restTemplate.postForObject(url, request, String.class);

        // extracts encoded polyline from response
        JSONObject json = new JSONObject(response);
        return json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("polyline")
                .getString("encodedPolyline");
    }

    // handling loop routes
    public String getWalkingPolylineForLoop(
            double startLat,
            double startLng,
            List<ScenicPoint> points
    ) {
        String url = "https://routes.googleapis.com/directions/v2:computeRoutes";

        // All pois become intermediate waypoints (loop back to start)
        JSONArray intermediates = new JSONArray();
        for (ScenicPoint p : points) {
            JSONObject waypoint = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", p.getLatitude())
                                    .put("longitude", p.getLongitude())
                            )
                    );
            intermediates.put(waypoint);
        }

        // Origin and destination are the same
        JSONObject body = new JSONObject()
                .put("travelMode", "WALK")
                .put("origin", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", startLat)
                                        .put("longitude", startLng))))
                .put("destination", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", startLat)
                                        .put("longitude", startLng))))
                .put("intermediates", intermediates);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.polyline.encodedPolyline");

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        String response = restTemplate.postForObject(url, request, String.class);

        JSONObject json = new JSONObject(response);
        return json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("polyline")
                .getString("encodedPolyline");
    }

    // generates polyline for point-to-point routes
    public String getWalkingPolylinePointToPoint(
            double startLat,
            double startLng,
            double endLat,
            double endLng,
            List<ScenicPoint> points
    ) {
        String url = "https://routes.googleapis.com/directions/v2:computeRoutes";

        // all pois are intermediate
        JSONArray intermediates = new JSONArray();
        for (ScenicPoint p : points) {
            JSONObject waypoint = new JSONObject()
                    .put("location", new JSONObject()
                            .put("latLng", new JSONObject()
                                    .put("latitude", p.getLatitude())
                                    .put("longitude", p.getLongitude())
                            )
                    );
            intermediates.put(waypoint);
        }

        JSONObject body = new JSONObject()
                .put("travelMode", "WALK")
                .put("origin", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", startLat)
                                        .put("longitude", startLng))))
                .put("destination", new JSONObject()
                        .put("location", new JSONObject()
                                .put("latLng", new JSONObject()
                                        .put("latitude", endLat)
                                        .put("longitude", endLng))));

        if (!intermediates.isEmpty()) body.put("intermediates", intermediates);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);
        headers.set("X-Goog-FieldMask", "routes.polyline.encodedPolyline");

        HttpEntity<String> request = new HttpEntity<>(body.toString(), headers);
        String response = restTemplate.postForObject(url, request, String.class);

        JSONObject json = new JSONObject(response);
        return json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("polyline")
                .getString("encodedPolyline");
    }
}
