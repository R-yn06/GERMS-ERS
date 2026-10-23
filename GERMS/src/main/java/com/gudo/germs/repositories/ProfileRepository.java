package com.gudo.germs.repositories;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.models.Profile;
import com.gudo.germs.supabase.RestClient;
import com.gudo.germs.supabase.SupabaseException;

import java.util.Optional;
import java.util.UUID;

public final class ProfileRepository {
    private final RestClient rest;

    public ProfileRepository(RestClient rest) { this.rest = rest; }

    public Optional<Profile> findById(UUID id) throws SupabaseException {
        JsonNode rows = rest.select("profiles", "select=id,full_name,email,role,is_active&id=eq." + id);
        return rows.isArray() && rows.size() > 0 ? Optional.of(Profile.fromJson(rows.get(0))) : Optional.empty();
    }
}
