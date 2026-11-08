package com.gudo.germs.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Service for device location detection, landmark/address geocoding, and reverse geocoding.
 * Uses OpenStreetMap Nominatim for geocoding and offers explicitly approximate public-IP location.
 */
public final class GeoLocationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String USER_AGENT = "GERMS-Emergency-Response-System/1.0 (PRC Davao Operations)";

    public record GeoPoint(double lat, double lon, String label) {}
    public record SearchResult(String displayName, double lat, double lon, String type) {
        @Override public String toString() { return displayName; }
    }
    public record SearchResponse(List<SearchResult> results, boolean onlineSearchAvailable, String error) {}

    // Default Davao City coordinates (Red Cross Davao Chapter vicinity)
    public static final double DEFAULT_LAT = 7.0731;
    public static final double DEFAULT_LON = 125.6128;

    private static final List<SearchResult> LOCAL_LANDMARKS = List.of(
            new SearchResult("Philippine Red Cross - Davao Chapter, Roxas Ave", 7.0712, 125.6145, "Emergency Services"),
            new SearchResult("Davao City Hall, San Pedro St, Poblacion", 7.0648, 125.6089, "Government"),
            new SearchResult("Southern Philippines Medical Center (SPMC), Bajada", 7.0988, 125.6263, "Hospital"),
            new SearchResult("Davao Doctors Hospital, Quirino Ave", 7.0673, 125.6052, "Hospital"),
            new SearchResult("People's Park, Palma Gil St", 7.0706, 125.6084, "Park / Landmark"),
            new SearchResult("Central 911 Headquarters, Sandawa, Matina", 7.0583, 125.5992, "Emergency Services"),
            new SearchResult("SM City Davao, Quimpo Blvd, Ecoland", 7.0494, 125.5905, "Commercial"),
            new SearchResult("SM Lanang Premier, J.P. Laurel Ave", 7.0989, 125.6331, "Commercial"),
            new SearchResult("Francisco Bangoy International Airport (DVO)", 7.1253, 125.6456, "Airport"),
            new SearchResult("Matina Crossing / GSIS Heights", 7.0543, 125.5847, "Barangay / Junction"),
            new SearchResult("Toril Public Market / Central Toril", 7.0189, 125.4988, "Barangay / Junction"),
            new SearchResult("Buhangin Central / Milan Buhangin", 7.1089, 125.6189, "Barangay / Junction"),
            new SearchResult("Calinan Poblacion / Central Calinan", 7.1856, 125.4578, "Barangay / Junction"),
            new SearchResult("Bunawan Poblacion / Bunawan Highway", 7.2289, 125.6472, "Barangay / Junction"),
            new SearchResult("Tibungco Public Market / Port Area", 7.1722, 125.6589, "Barangay / Port"),
            new SearchResult("Bangkerohan Public Market", 7.0681, 125.6042, "Market / Junction")
    );

    private GeoLocationService() {}

    /**
     * Returns an approximate network-IP location, not GPS or operating-system device location.
     * The caller must explain the third-party lookup and obtain the user's consent first.
     */
    public static GeoPoint detectApproximateLocationByIp() throws IOException {
        URI uri = URI.create("https://ipapi.co/json/");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Location lookup failed (HTTP " + status + ").");
            }
            try (InputStream in = conn.getInputStream()) {
                JsonNode root = JSON.readTree(in);
                if (root.hasNonNull("error") && root.path("error").asBoolean()) {
                    throw new IOException(root.path("reason").asText("Location lookup was rejected."));
                }
                double lat = root.path("latitude").asDouble(Double.NaN);
                double lon = root.path("longitude").asDouble(Double.NaN);
                if (!validCoordinates(lat, lon)) {
                    throw new IOException("The location service did not return valid coordinates.");
                }
                String city = root.path("city").asText("Approximate location");
                String region = root.path("region").asText("");
                return new GeoPoint(lat, lon, city + (region.isBlank() ? "" : ", " + region));
            }
        } finally {
            conn.disconnect();
        }
    }

    public static boolean validCoordinates(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon)
                && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    /**
     * Search landmarks, streets, and points of interest using OpenStreetMap Nominatim.
     * Combines with local Davao landmark matches when online search is unavailable.
     */
    public static SearchResponse search(String query) {
        if (query == null || query.trim().length() < 2) {
            return new SearchResponse(List.of(), true, null);
        }
        String q = query.trim().toLowerCase(Locale.ROOT);

        List<SearchResult> results = new ArrayList<>();

        // 1. Check local landmark matches first (instant, works offline)
        for (SearchResult lm : LOCAL_LANDMARKS) {
            if (lm.displayName().toLowerCase(Locale.ROOT).contains(q)) {
                results.add(lm);
            }
        }

        // 2. Perform a live OSM Nominatim query.
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
            String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=6&addressdetails=1";
            URI uri = URI.create(urlStr);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept-Language", "en,fil");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4500);

            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                return new SearchResponse(List.copyOf(results), false, "Online map search failed (HTTP " + status + ").");
            }
            try (InputStream in = conn.getInputStream()) {
                JsonNode root = JSON.readTree(in);
                if (!root.isArray()) {
                    return new SearchResponse(List.copyOf(results), false, "Online map search returned an invalid response.");
                }
                for (JsonNode item : root) {
                    String name = item.path("display_name").asText();
                    double lat = item.path("lat").asDouble(Double.NaN);
                    double lon = item.path("lon").asDouble(Double.NaN);
                    String type = item.path("type").asText("landmark");
                    if (name.isBlank() || !validCoordinates(lat, lon)) continue;

                    boolean duplicate = results.stream().anyMatch(r ->
                            Math.abs(r.lat() - lat) < 0.0005 && Math.abs(r.lon() - lon) < 0.0005);
                    if (!duplicate) {
                        results.add(new SearchResult(name, lat, lon, type));
                    }
                }
            }
            return new SearchResponse(List.copyOf(results), true, null);
        } catch (IOException | IllegalArgumentException e) {
            return new SearchResponse(List.copyOf(results), false, e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Reverse geocodes a coordinate to a readable address or street name.
     */
    public static String reverseGeocode(double lat, double lon) throws IOException {
        if (!validCoordinates(lat, lon)) throw new IllegalArgumentException("Invalid latitude or longitude.");
        String urlStr = String.format(Locale.ROOT,
                "https://nominatim.openstreetmap.org/reverse?lat=%.6f&lon=%.6f&format=json&zoom=18&addressdetails=1",
                lat, lon);
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept-Language", "en,fil");
        conn.setConnectTimeout(3500);
        conn.setReadTimeout(3500);
        try {
            int status = conn.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Address lookup failed (HTTP " + status + ").");
            }
            try (InputStream in = conn.getInputStream()) {
                JsonNode root = JSON.readTree(in);
                String displayName = root.path("display_name").asText(null);
                if (displayName == null || displayName.isBlank()) {
                    throw new IOException("No address was found for this map pin.");
                }
                return displayName;
            }
        } finally {
            conn.disconnect();
        }
    }
}
