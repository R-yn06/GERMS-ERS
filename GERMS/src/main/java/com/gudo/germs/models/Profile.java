package com.gudo.germs.models;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Represents a user's profile and application role. */
public record Profile(UUID id, String fullName, String email, Role role, boolean active) {
    /** Creates a profile from a Supabase JSON record, applying defaults for optional fields. */
    public static Profile fromJson(JsonNode n) {
        return new Profile(UUID.fromString(n.get("id").asText()), n.path("full_name").asText(),
                n.path("email").asText(""), Role.fromDb(n.path("role").asText()), n.path("is_active").asBoolean(true));
    }

    /** Creates a guest profile with the default guest role. */
    public static Profile guest() { return new Profile(new UUID(0, 0), "Guest", "", Role.GUEST, true); }
}
