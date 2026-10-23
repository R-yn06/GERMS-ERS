package com.gudo.germs.supabase;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Supabase Auth (GoTrue) REST calls. Passwords are sent to Supabase only; never stored. */
public final class AuthClient {
    private final SupabaseHttp http;

    public AuthClient(SupabaseHttp http) { this.http = http; }

    public Session signIn(String email, String password) throws SupabaseException {
        String body = SupabaseHttp.JSON.createObjectNode().put("email", email).put("password", password).toString();
        return parse(http.send("POST", "/auth/v1/token?grant_type=password", body, null, null));
    }

    public Session refresh(String refreshToken) throws SupabaseException {
        String body = SupabaseHttp.JSON.createObjectNode().put("refresh_token", refreshToken).toString();
        return parse(http.send("POST", "/auth/v1/token?grant_type=refresh_token", body, null, null));
    }

    public void signOut(String accessToken) throws SupabaseException {
        http.send("POST", "/auth/v1/logout", null, accessToken, null);
    }

    private static Session parse(JsonNode n) throws SupabaseException {
        if (!n.hasNonNull("access_token") || !n.path("user").hasNonNull("id")) {
            throw new SupabaseException(500, "Unexpected response from Supabase Auth");
        }
        long ttl = n.path("expires_in").asLong(3600);
        return new Session(n.get("access_token").asText(), n.path("refresh_token").asText(),
                Instant.now().plusSeconds(ttl), UUID.fromString(n.path("user").get("id").asText()),
                n.path("user").path("email").asText(""));
    }
}
