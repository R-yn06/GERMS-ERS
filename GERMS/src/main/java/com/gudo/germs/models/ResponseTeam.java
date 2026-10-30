package com.gudo.germs.models;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record ResponseTeam(UUID id, String name) {
    public static ResponseTeam fromJson(JsonNode row) {
        return new ResponseTeam(UUID.fromString(row.path("id").asText()), row.path("name").asText());
    }

    @Override public String toString() { return name; }
}
