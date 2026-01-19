package com.martyna.ScenicRoutes.service;

import com.martyna.ScenicRoutes.model.*;
import com.martyna.ScenicRoutes.model.SightseeingSchedule.Break;
import com.martyna.ScenicRoutes.model.SightseeingSchedule.ScheduledStop;
import com.martyna.ScenicRoutes.model.UserPreferences.POICategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
// Generates a time-aware longer sightseeing plan that balances user preferences,
// walking distances, and fixed daily time windows. As opposed to short walking routes,
// this service produces schedules with arrival/departure time and optional breaks.
@Service
public class SightseeingSchedulerService {

    @Autowired
    private GooglePlacesService placesService;

    @Autowired
    private ImportanceScorer scorer;

    @Autowired
    private WalkingTimeCache cache;

    public SightseeingSchedule generateSchedule(
            double startLat, double startLng,
            String startTimeStr, String endTimeStr,
            UserPreferences preferences,
            boolean includeLunchBreak
    ) {
        LocalTime startTime = LocalTime.parse(startTimeStr);
        LocalTime endTime = LocalTime.parse(endTimeStr);


        List<ScenicPoint> allPois = placesService.getNearbyPOIs(startLat, startLng, true);
        List<SightseeingAttraction> candidates = scorer.selectTopAttractions(allPois, preferences);

        // Separate by preference
        List<SightseeingAttraction> boosted = new ArrayList<>();
        List<SightseeingAttraction> optional = new ArrayList<>();

        for (SightseeingAttraction attr : candidates) {
            if (attr.getWrappedPOI().shouldBeExcluded(preferences)) continue;

            double weight = preferences.getCategoryWeight(attr.getPrimaryCategory());
            if (weight >= 2.0) {
                boosted.add(attr);
            } else if (weight > 0.0) {
                optional.add(attr);
            }
        }

        return buildSchedule(
                startLat, startLng, boosted, optional, startTime, endTime, includeLunchBreak
        );
    }

    private SightseeingSchedule buildSchedule(
            double startLat, double startLng,
            List<SightseeingAttraction> boosted,
            List<SightseeingAttraction> optional,
            LocalTime startTime,
            LocalTime endTime,
            boolean includeLunch
    ) {
        SightseeingSchedule schedule = new SightseeingSchedule();
        List<ScheduledStop> stops = new ArrayList<>();
        List<Break> breaks = new ArrayList<>();
        Set<SightseeingAttraction> used = new HashSet<>();

        LocalTime currentTime = startTime;
        double currentLat = startLat;
        double currentLng = startLng;
        boolean hadLunch = false;

        // pick 1 poi per boosted category (with proximity check, time checks)
        Map<POICategory, List<SightseeingAttraction>> byCategory = new HashMap<>();
        for (SightseeingAttraction attr : boosted) {
            byCategory.computeIfAbsent(attr.getPrimaryCategory(), k -> new ArrayList<>()).add(attr);
        }

        for (List<SightseeingAttraction> list : byCategory.values()) {
            list.sort((a, b) -> Integer.compare(b.getImportanceScore(), a.getImportanceScore()));
        }

        List<SightseeingAttraction> guaranteed = new ArrayList<>();

        for (POICategory category : byCategory.keySet()) {
            List<SightseeingAttraction> inCategory = byCategory.get(category);
            SightseeingAttraction best = inCategory.get(0);

            // Proximity check
            if (!guaranteed.isEmpty()) {
                int walkTime = cache.getWalkingTimeMinutes(currentLat, currentLng, best.getLatitude(), best.getLongitude());

                if (walkTime > 30) {
                    // Try to find closer alternative
                    for (SightseeingAttraction alt : inCategory) {
                        int altWalk = cache.getWalkingTimeMinutes(currentLat, currentLng, alt.getLatitude(), alt.getLongitude());
                        if (altWalk <= 30) {
                            best = alt;
                            break;
                        }
                    }
                }

                // Update current position for next proximity check
                currentLat = best.getLatitude();
                currentLng = best.getLongitude();
            }

            guaranteed.add(best);
        }

        // Add other boosted attractions (not just 1 per category)
        List<SightseeingAttraction> remainingBoosted = new ArrayList<>();
        for (SightseeingAttraction attr : boosted) {
            if (!guaranteed.contains(attr)) {
                remainingBoosted.add(attr);
            }
        }

        // add optional attractions
        List<SightseeingAttraction> allOptional = new ArrayList<>(optional);

        // guaranteed first, then remaining boosted, then optional
        List<SightseeingAttraction> toSchedule = new ArrayList<>();
        toSchedule.addAll(guaranteed);
        toSchedule.addAll(remainingBoosted);
        toSchedule.addAll(allOptional);


        // Reset position to start
        currentLat = startLat;
        currentLng = startLng;
        currentTime = startTime;

        while (!toSchedule.isEmpty()) {
            int timeLeft = (int) Duration.between(currentTime, endTime).toMinutes();
            if (timeLeft < 20) break;

            // Find nearest attraction that fits
            SightseeingAttraction nearest = null;
            int nearestWalk = Integer.MAX_VALUE;

            for (SightseeingAttraction attr : toSchedule) {
                if (used.contains(attr)) continue;

                int walk = cache.getWalkingTimeMinutes(currentLat, currentLng, attr.getLatitude(), attr.getLongitude());
                int total = walk + attr.getVisitDuration();

                // Account for lunch
                int lunchBuffer = 0;
                LocalTime afterWalk = currentTime.plusMinutes(walk);
                LocalTime afterVisit = afterWalk.plusMinutes(attr.getVisitDuration());
                if (includeLunch && !hadLunch && afterVisit.getHour() >= 13) {
                    lunchBuffer = 60;
                }

                if (total + lunchBuffer <= timeLeft && walk < nearestWalk) {
                    nearest = attr;
                    nearestWalk = walk;
                }
            }

            if (nearest == null) {
                // Try forcing lunch if we haven't had it
                if (includeLunch && !hadLunch && currentTime.getHour() >= 11 && timeLeft >= 80) {
                    breaks.add(new Break(currentTime.toString(), 60, "LUNCH", "Restaurants nearby"));
                    currentTime = currentTime.plusMinutes(60);
                    hadLunch = true;
                    continue;
                }
                break;
            }

            // Check if lunch should be inserted before this attraction
            currentTime = currentTime.plusMinutes(nearestWalk);
            LocalTime afterVisit = currentTime.plusMinutes(nearest.getVisitDuration());

            if (includeLunch && !hadLunch && currentTime.getHour() >= 11 && afterVisit.getHour() >= 13) {
                // Insert lunch BEFORE visiting
                currentTime = currentTime.minusMinutes(nearestWalk); // Go back
                breaks.add(new Break(currentTime.toString(), 60, "LUNCH", "Restaurants nearby"));
                currentTime = currentTime.plusMinutes(60);
                hadLunch = true;

                // Recalculate walk after lunch
                nearestWalk = cache.getWalkingTimeMinutes(currentLat, currentLng, nearest.getLatitude(), nearest.getLongitude());
                currentTime = currentTime.plusMinutes(nearestWalk);
            }

            LocalTime arrival = currentTime;
            LocalTime departure = arrival.plusMinutes(nearest.getVisitDuration());


            stops.add(new ScheduledStop(nearest, arrival.toString(), departure.toString(),
                    nearest.getVisitDuration(), nearestWalk));

            used.add(nearest);
            toSchedule.remove(nearest);
            currentTime = departure;
            currentLat = nearest.getLatitude();
            currentLng = nearest.getLongitude();
        }

        // Final lunch check
        if (includeLunch && !hadLunch && currentTime.getHour() < 15) {
            breaks.add(new Break(currentTime.toString(), 60, "LUNCH", "Restaurants nearby"));
            currentTime = currentTime.plusMinutes(60);
        }

        schedule.setStops(stops);
        schedule.setBreaks(breaks);
        schedule.setTotalMinutes((int) Duration.between(startTime, currentTime).toMinutes());

        if (!stops.isEmpty()) {
            List<ScenicPoint> points = stops.stream()
                    .map(s -> s.getAttraction().getWrappedPOI())
                    .collect(Collectors.toList());
            schedule.setPolyline(cache.getWalkingPolylineWithWaypoints(startLat, startLng, points));
        }

        int leftover = (int) Duration.between(currentTime, endTime).toMinutes();

        return schedule;
    }

    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371000;
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private boolean isEverythingAvoided(UserPreferences preferences) {
        POICategory[] majorCategories = {
                POICategory.MUSEUM, POICategory.AQUARIUM, POICategory.ZOO,
                POICategory.OBSERVATION_DECK, POICategory.HISTORIC_SITE,
                POICategory.LANDMARK, POICategory.PARK
        };

        int avoided = 0;
        for (POICategory cat : majorCategories) {
            if (preferences.getCategoryWeight(cat) == 0.0) avoided++;
        }
        return avoided == majorCategories.length;
    }
}
