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

    public Profile ensureGuestProfile(UUID id, String fullName, String email, String phone) {
        try {
            rest.insertMinimal("profiles", java.util.Map.of(
                "id", id.toString(),
                "full_name", (fullName != null && !fullName.isBlank()) ? fullName : (email != null ? email : "Guest User"),
                "email", email != null ? email : "",
                "phone", phone != null ? phone : "",
                "role", "guest",
                "is_active", true
            ));
        } catch (Exception ignored) {}
        try {
            return findById(id).orElse(new Profile(id, fullName != null && !fullName.isBlank() ? fullName : "Guest User", email != null ? email : "", com.gudo.germs.models.Role.GUEST, true));
        } catch (Exception e) {
            return new Profile(id, fullName != null && !fullName.isBlank() ? fullName : "Guest User", email != null ? email : "", com.gudo.germs.models.Role.GUEST, true);
        }
    }
}
