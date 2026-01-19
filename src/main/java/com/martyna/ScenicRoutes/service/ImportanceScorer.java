package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import com.martyna.ScenicRoutes.model.SightseeingAttraction;
import com.martyna.ScenicRoutes.model.UserPreferences;
import com.martyna.ScenicRoutes.model.UserPreferences.POICategory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
// Helper class figuring out the importance/weight of a given POI based on the
// type, popularity (n. of reviews), and user preferences
@Service
public class ImportanceScorer {

    public int calculateImportance(ScenicPoint poi) {
        int score = 0;

        int reviews = poi.getReviewCount();
        if (reviews > 100000) score += 100;
        else if (reviews > 50000) score += 80;
        else if (reviews > 20000) score += 60;
        else if (reviews > 10000) score += 40;
        else if (reviews > 5000) score += 20;
        else if (reviews > 2000) score += 10;

        double rating = poi.getRating();
        if (rating >= 4.7) score += 20;
        else if (rating >= 4.5) score += 15;
        else if (rating >= 4.3) score += 10;
        else if (rating >= 4.0) score += 5;
        else if (rating < 4.0) score -= 20;

        POICategory category = poi.getPrimaryCategory();
        if (category == POICategory.MUSEUM) {
            score += 30;
        } else if (category == POICategory.AQUARIUM || category == POICategory.ZOO) {
            score += 25;
        } else if (category == POICategory.OBSERVATION_DECK || category == POICategory.HISTORIC_SITE) {
            score += 20;
        }

        List<String> types = poi.getTypes();
        if (types.contains("tourist_attraction")) score += 20;
        if (types.contains("landmark")) score += 15;
        if (types.contains("point_of_interest")) score += 5;

        String name = poi.getName().toLowerCase();
        if (name.contains("national")) score += 15;
        if (name.contains("museum of")) score += 15;
        if (name.contains("memorial")) score+= 10;
        if (name.contains("tower") || name.contains("building")) score += 10;
        if (name.contains("palace") || name.contains("castle")) score += 15;
        if (name.contains("cathedral") || name.contains("basilica")) score += 10;
        if (name.contains("park")) score += 5;

        return Math.max(0, score);
    }

    public List<SightseeingAttraction> selectTopAttractions(
            List<ScenicPoint> allPois,
            UserPreferences preferences
    ) {

        List<SightseeingAttraction> attractions = allPois.stream()
                .map(poi -> new SightseeingAttraction(poi))
                .collect(Collectors.toList());

        // Calculate importance scores
        for (SightseeingAttraction attr : attractions) {
            attr.setImportanceScore(calculateImportance(attr.getWrappedPOI()));
        }

        int minScore = 30;
        List<SightseeingAttraction> qualified = attractions.stream()
                .filter(attr -> attr.getImportanceScore() >= minScore)
                .collect(Collectors.toList());


        // Apply preference
        qualified = applyPreferenceBoost(qualified, preferences);

        // Filter out avoided categories
        qualified = qualified.stream()
                .filter(attr -> attr.getImportanceScore() > 0)
                .collect(Collectors.toList());

        // Now select top 15
        List<SightseeingAttraction> topAttractions = qualified.stream()
                .sorted(Comparator.comparingInt(SightseeingAttraction::getImportanceScore).reversed())
                .limit(15)
                .collect(Collectors.toList());

        topAttractions.forEach(attr -> {
            POICategory category = attr.getPrimaryCategory();

        });

        return topAttractions;
    }

    private List<SightseeingAttraction> applyPreferenceBoost(
            List<SightseeingAttraction> attractions,
            UserPreferences preferences
    ) {
        if (!preferences.hasCustomPreferences()) {
            return attractions;
        }


        for (SightseeingAttraction attr : attractions) {
            POICategory category = attr.getPrimaryCategory();
            double weight = preferences.getCategoryWeight(category);

            if (weight > 1.0) {
                int originalScore = attr.getImportanceScore();
                int boostedScore = (int)(originalScore * weight);
                attr.setImportanceScore(boostedScore);

            } else if (weight == 0.0) {
                // Set to 0 to mark for exclusion
                attr.setImportanceScore(0);
            }
        }

        return attractions;
    }
}
