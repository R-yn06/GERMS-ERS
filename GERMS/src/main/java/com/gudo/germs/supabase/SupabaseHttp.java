package com.gudo.germs.supabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gudo.germs.config.AppConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Single place that talks HTTP to Supabase. Sends only the public anon key plus the caller's JWT. */
public final class SupabaseHttp {
    public static final ObjectMapper JSON = new ObjectMapper();

    private final AppConfig config;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseHttp(AppConfig config) { this.config = config; }

    /** @param path path beginning with "/", e.g. "/rest/v1/incidents?select=*"
     *  @param accessToken user JWT, or null for anonymous (guest) access */
    public JsonNode send(String method, String path, String jsonBody, String accessToken, Map<String, String> headers)
            throws SupabaseException {
        if (!config.isConfigured()) {
            throw new SupabaseException(0, "Supabase is not configured. Copy application.properties.example to application.properties.");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(config.supabaseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", config.anonKey())
                .header("Authorization", "Bearer " + (accessToken != null ? accessToken : config.anonKey()))
                .header("Accept", "application/json");
        if (headers != null) headers.forEach(b::header);
        if (jsonBody != null) {
            b.header("Content-Type", "application/json");
            b.method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> res = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
            String body = res.body() == null ? "" : res.body();
            if (res.statusCode() / 100 != 2) throw new SupabaseException(res.statusCode(), extractMessage(body, res.statusCode()));
            return body.isBlank() ? JSON.nullNode() : JSON.readTree(body);
        } catch (IOException e) {
            throw new SupabaseException(0, "Cannot reach Supabase: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SupabaseException(0, "Request interrupted");
        }
    }

    private static String extractMessage(String body, int status) {
        try {
            JsonNode n = JSON.readTree(body);
            for (String f : new String[] {"message", "msg", "error_description", "error"}) {
                if (n.hasNonNull(f)) return n.get(f).asText();
            }
        } catch (IOException ignored) { /* fall through */ }
        return "Request failed (HTTP " + status + ")";
    }
}
