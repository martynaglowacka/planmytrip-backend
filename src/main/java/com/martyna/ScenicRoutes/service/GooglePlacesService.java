package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GooglePlacesService {

    @Value("${google.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<ScenicPoint> getNearbyPOIs(double lat, double lng) {
        return getNearbyPOIs(lat, lng, false);
    }
// include museums for sightseeing mode (normally excluded for walking mode)
    public List<ScenicPoint> getNearbyPOIs(double lat, double lng, boolean includeMuseums) {
        List<ScenicPoint> allPoints = new ArrayList<>();

        // more results
        String nextPageToken = null;
        int pageCount = 0;
        int maxPages = 3;

        do {
            String url = buildSearchUrl(lat, lng, nextPageToken);

            String response = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(response);

            if (json.has("results")) {
                JSONArray results = json.getJSONArray("results");
                List<ScenicPoint> pagePoints = processResults(results, includeMuseums);
                allPoints.addAll(pagePoints);

            }

            // Get next page token
            nextPageToken = json.has("next_page_token")
                    ? json.getString("next_page_token")
                    : null;

            pageCount++;

            if (nextPageToken != null && pageCount < maxPages) {
                try {
                    Thread.sleep(2000); // 2 second delay required by Google
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } while (nextPageToken != null && pageCount < maxPages);

        return allPoints;
    }

    public List<ScenicPoint> searchByType(double lat, double lng, String type) {
        System.out.println("  Searching Google Places for type: " + type);

        String url = "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
                + "?location=" + lat + "," + lng
                + "&radius=1500"  // 1.5km radius - reasonable walking distance
                + "&type=" + type
                + "&rankby=prominence"  // Get best/most popular results
                + "&key=" + apiKey;

        try {
            String response = restTemplate.getForObject(url, String.class);
            JSONObject json = new JSONObject(response);

            if (json.has("results")) {
                JSONArray results = json.getJSONArray("results");
                List<ScenicPoint> points = processResults(results, true);

                // Sort by score and take top 20 to avoid too many low-quality results
                points.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
                List<ScenicPoint> topPoints = points.stream()
                        .limit(20)
                        .collect(Collectors.toList());

                return topPoints;
            }
        } catch (Exception e) {
            System.err.println("  ✗ Error searching for type " + type + ": " + e.getMessage());
        }

        return new ArrayList<>();
    }

    private String buildSearchUrl(double lat, double lng, String pageToken) {
        StringBuilder url = new StringBuilder();
        url.append("https://maps.googleapis.com/maps/api/place/nearbysearch/json");
        url.append("?location=").append(lat).append(",").append(lng);
        url.append("&radius=3000");
        // Added more types to catch landmarks like Empire State Building
        url.append("&type=tourist_attraction|point_of_interest|landmark|park|museum|art_gallery|cafe|restaurant");
        url.append("&key=").append(apiKey);

        if (pageToken != null) {
            url.append("&pagetoken=").append(pageToken);
        }

        return url.toString();
    }

    private List<ScenicPoint> processResults(JSONArray results, boolean includeMuseums) {
        List<ScenicPoint> scenicPoints = new ArrayList<>();

        for (int i = 0; i < results.length(); i++) {
            JSONObject place = results.getJSONObject(i);

            String name = place.getString("name");
            double placeLat = place.getJSONObject("geometry")
                    .getJSONObject("location")
                    .getDouble("lat");
            double placeLng = place.getJSONObject("geometry")
                    .getJSONObject("location")
                    .getDouble("lng");

            double rating = place.has("rating") ? place.getDouble("rating") : 3.0;
            int numReviews = place.has("user_ratings_total")
                    ? place.getInt("user_ratings_total")
                    : 0;

            // photo urls
            String photoUrl = null;
            if (place.has("photos")) {
                JSONArray photos = place.getJSONArray("photos");
                if (photos.length() > 0) {
                    JSONObject photo = photos.getJSONObject(0);
                    String photoReference = photo.getString("photo_reference");
                    // Build Google Photos API URL
                    photoUrl = "https://maps.googleapis.com/maps/api/place/photo"
                            + "?maxwidth=400"
                            + "&photoreference=" + photoReference
                            + "&key=" + apiKey;
                }
            }
            List<String> types = new ArrayList<>();
            if (place.has("types")) {
                JSONArray typesArray = place.getJSONArray("types");
                for (int j = 0; j < typesArray.length(); j++) {
                    types.add(typesArray.getString(j));
                }
            }


            // museums removed - no sense for quick walking route
            if (!includeMuseums && types.contains("museum")) continue;


            double score = calculateImprovedScore(rating, numReviews, types, name);

            ScenicPoint point = new ScenicPoint(name, placeLat, placeLng, score, types, numReviews, rating);
            point.setPhotoUrl(photoUrl);
            scenicPoints.add(point);
        }

        return scenicPoints;
    }

    private double calculateImprovedScore(double rating, int numReviews, List<String> types, String name) {
        // Cap reviews at 50,000 to prevent too popular routes
        int cappedReviews = Math.min(numReviews, 50000);

        // base score
        double baseScore = rating * Math.sqrt(cappedReviews + 1);

        // Type multipliers - boost important POI types
        double typeMultiplier = 1.0;
        if (types.contains("tourist_attraction")) {
            typeMultiplier = 1.5;
        }
        if (types.contains("park") && numReviews > 10000) {
            typeMultiplier = Math.max(typeMultiplier, 1.4);
        }
        if (types.contains("point_of_interest")) {
            typeMultiplier = Math.max(typeMultiplier, 1.2);
        }
        if (types.contains("landmark")) {
            typeMultiplier = Math.max(typeMultiplier, 1.4);
        }
        if (types.contains("museum")) {
            typeMultiplier = Math.max(typeMultiplier, 1.5);
        }

        // Popularity bonus
        double popularityBonus = 1.0;
        if (numReviews > 50000) {
            popularityBonus = 1.5;
        } else if (numReviews > 20000) {
            popularityBonus = 1.3;
        } else if (numReviews > 5000) {
            popularityBonus = 1.15;
        }

        // hidden gem boost
        double hiddenGemBoost = 1.0;
        if (numReviews >= 100 && numReviews <= 2000 && rating >= 4.5) {
            hiddenGemBoost = 3.0;
            System.out.println("HIDDEN GEM BOOST: " + name +
                    " (reviews: " + numReviews + ", rating: " + rating + ") → 3x multiplier");
        }

        return baseScore * typeMultiplier * popularityBonus * hiddenGemBoost;
    }
}
