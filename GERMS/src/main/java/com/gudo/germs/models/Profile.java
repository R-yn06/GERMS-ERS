package com.gudo.germs.models;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record Profile(UUID id, String fullName, String email, Role role, boolean active) {
    public static Profile fromJson(JsonNode n) {
        return new Profile(UUID.fromString(n.get("id").asText()), n.path("full_name").asText(),
                n.path("email").asText(""), Role.fromDb(n.path("role").asText()), n.path("is_active").asBoolean(true));
    }

    public static Profile guest() { return new Profile(new UUID(0, 0), "Guest", "", Role.GUEST, true); }
}
