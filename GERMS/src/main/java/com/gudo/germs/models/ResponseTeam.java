package com.gudo.germs.models;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** Represents a response team assigned to an incident. */
public record ResponseTeam(UUID id, String name) {
    /** Creates a response team from a Supabase JSON record. */
    public static ResponseTeam fromJson(JsonNode row) {
        return new ResponseTeam(UUID.fromString(row.path("id").asText()), row.path("name").asText());
    }

    /** Returns a string representation of this value. */
    @Override public String toString() { return name; }
}
