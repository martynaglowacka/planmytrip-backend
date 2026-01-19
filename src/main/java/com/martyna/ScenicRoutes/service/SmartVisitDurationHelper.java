package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.ScenicPoint;
import com.martyna.ScenicRoutes.model.UserPreferences.POICategory;

public class SmartVisitDurationHelper {

    // Get visit duration based on category, reviews, and rating
    public static int getSmartVisitMinutes(ScenicPoint poi, POICategory category) {
        return getSmartVisitMinutes(poi, category, 1.0);
    }

    // Get visit duration with a time multiplier for flexible scheduling
    public static int getSmartVisitMinutes(ScenicPoint poi, POICategory category, double timeMultiplier) {
        if (category == null) return 15;

        int baseMinutes = getBaseDuration(category);
        int reviewCount = poi.getReviewCount();
        double rating = poi.getRating();

        // Apply adjustments based on popularity and quality
        double multiplier = calculateDurationMultiplier(category, reviewCount, rating);

        // Apply time multiplier for scheduling flexibility
        int finalMinutes = (int) Math.round(baseMinutes * multiplier * timeMultiplier);

        // Ensure reasonable bounds based on category
        int min = getMinDuration(category);
        int max = getMaxDuration(category);

        return Math.min(Math.max(finalMinutes, min), max);
    }

    private static int getMinDuration(POICategory category) {
        switch (category) {
            case MUSEUM:
            case AQUARIUM:
            case ZOO:
                return 60; // assuming major attractions need at least 1 hour
            case OBSERVATION_DECK:
            case HISTORIC_SITE:
                return 30;
            default:
                return 10;
        }
    }

    private static int getMaxDuration(POICategory category) {
        switch (category) {
            case MUSEUM:
                return 240;
            case AQUARIUM:
            case ZOO:
                return 210;
            case OBSERVATION_DECK:
            case HISTORIC_SITE:
                return 90;
            default:
                return 120;
        }
    }

    // Base duration by category type
    private static int getBaseDuration(POICategory category) {
        switch (category) {
            // Major attractions - long visits
            case MUSEUM:
                return 120;
            case AQUARIUM:
                return 90;
            case ZOO:
                return 150;
            case OBSERVATION_DECK:
                return 45;

            // Cultural sites
            case HISTORIC_SITE:
                return 45;
            case THEATER:
                return 90; // Show length varies
            case CHURCH:
                return 25;

            // Urban landmarks
            case LANDMARK:
                return 20;
            case PARK:
                return 30;
            case VIEWPOINT:
                return 15;
            case SCULPTURE:
            case STREET_ART:
            case FOUNTAIN:
                return 10;

            // Shopping/dining
            case SHOPPING:
                return 45;
            case RESTAURANT:
                return 60;
            case CAFE:
                return 20;

            // Popularity-based
            case TRENDING:
                return 25;
            case HIDDEN_GEM:
                return 20;

            default:
                return 15;
        }
    }

    // Calculate duration multiplier based on attraction characteristics
    private static double calculateDurationMultiplier(POICategory category, int reviews, double rating) {
        double multiplier = 1.0;

        // MUSEUM: More popular = longer visit (more exhibits)
        if (category == POICategory.MUSEUM) {
            if (reviews > 50000) {
                multiplier = 1.5; // Major museums: 180 min
            } else if (reviews > 20000) {
                multiplier = 1.25;
            } else if (reviews < 5000) {
                multiplier = 0.75;
            }

            // High-rated museums deserve more time
            if (rating >= 4.7) {
                multiplier *= 1.1;
            }
        }

        else if (category == POICategory.ZOO || category == POICategory.AQUARIUM) {
            if (reviews > 30000) {
                multiplier = 1.3; // Major zoos
            } else if (reviews > 10000) {
                multiplier = 1.1;
            } else {
                multiplier = 0.8;
            }
        }

        // PARK: Popular parks = worth exploring longer
        else if (category == POICategory.PARK) {
            if (reviews > 50000) {
                multiplier = 2.0;
            } else if (reviews > 10000) {
                multiplier = 1.5;
            } else if (reviews < 2000) {
                multiplier = 0.7;
            }
        }

        // LANDMARK: Iconic ones deserve more time
        else if (category == POICategory.LANDMARK) {
            if (reviews > 100000) {
                multiplier = 1.5; // Times Square: 30 min
            } else if (reviews > 50000) {
                multiplier = 1.25; // Major landmarks: 25 min
            } else if (reviews < 10000) {
                multiplier = 0.75;
            }
        }

        // OBSERVATION_DECK: Major ones take longer
        else if (category == POICategory.OBSERVATION_DECK) {
            if (reviews > 50000) {
                multiplier = 1.5; // Major decks with lines: 70 min
            } else if (reviews > 10000) {
                multiplier = 1.2;
            }
        }

        // CHURCH: Famous cathedrals vs local churches
        else if (category == POICategory.CHURCH) {
            if (reviews > 20000) {
                multiplier = 1.4; // Notre Dame, St Patrick's: 35 min
            } else if (reviews > 5000) {
                multiplier = 1.1; // Notable churches: 28 min
            } else {
                multiplier = 0.8; // Small churches: 20 min
            }
        }

        // HISTORIC_SITE: Significant ones need more time
        else if (category == POICategory.HISTORIC_SITE) {
            if (reviews > 20000) {
                multiplier = 1.5; // Major sites: 70 min
            } else if (reviews > 5000) {
                multiplier = 1.2; // Important sites: 55 min
            }
        }

        // High ratings = worth the time
        else if (category == POICategory.HIDDEN_GEM) {
            if (rating >= 4.7) multiplier = 1.3;
            if (rating < 4.7 && rating >=4.5) multiplier = 1.1;
        }

        return multiplier;
    }


    public static int getDefaultVisitMinutes(POICategory category) {
        return getBaseDuration(category);
    }

    // Check if this is a major attraction, requiring significant time
    public static boolean isMajorAttraction(POICategory category) {
        if (category == null) return false;
        return category == POICategory.MUSEUM
                || category == POICategory.AQUARIUM
                || category == POICategory.ZOO
                || category == POICategory.OBSERVATION_DECK
                || category == POICategory.HISTORIC_SITE;
    }
}
