package com.gudo.germs.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Properties;

/** Loads public Supabase settings. Precedence: environment variables, ./application.properties, classpath. */
public final class AppConfig {
    private final String supabaseUrl;
    private final String anonKey;

    private AppConfig(String supabaseUrl, String anonKey) {
        this.supabaseUrl = supabaseUrl;
        this.anonKey = anonKey;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        Path local = Path.of("application.properties");
        try {
            if (Files.exists(local)) {
                try (InputStream in = Files.newInputStream(local)) { props.load(in); }
            } else {
                try (InputStream in = AppConfig.class.getResourceAsStream("/application.properties")) {
                    if (in != null) props.load(in);
                }
            }
        } catch (IOException e) {
            System.err.println("Could not read application.properties: " + e.getMessage());
        }
        String url = firstNonBlank(System.getenv("GERMS_SUPABASE_URL"), props.getProperty("supabase.url"));
        String key = firstNonBlank(System.getenv("GERMS_SUPABASE_ANON_KEY"), props.getProperty("supabase.anonKey"));
        if (isPrivilegedKey(key)) {
            throw new IllegalStateException("A privileged Supabase key was supplied. Use only the public anon/publishable key in the desktop app.");
        }
        return new AppConfig(url.replaceAll("/+$", ""), key);
    }

    public boolean isConfigured() {
        return !supabaseUrl.isBlank() && !anonKey.isBlank() && !supabaseUrl.contains("YOUR-PROJECT-REF");
    }

    public String supabaseUrl() { return supabaseUrl; }
    public String anonKey() { return anonKey; }

    /** Rejects service-role JWTs and new-style secret keys. */
    public static boolean isPrivilegedKey(String key) {
        if (key == null || key.isBlank()) return false;
        if (key.startsWith("sb_secret_")) return true;
        String[] parts = key.split("\\.");
        if (parts.length == 3) {
            try {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return payload.contains("\"service_role\"");
            } catch (IllegalArgumentException ignored) { /* not a JWT */ }
        }
        return false;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        return b == null ? "" : b.trim();
    }
}
