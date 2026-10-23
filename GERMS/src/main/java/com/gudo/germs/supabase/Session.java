package com.gudo.germs.supabase;

import java.time.Instant;
import java.util.UUID;

public record Session(String accessToken, String refreshToken, Instant expiresAt, UUID userId, String email) {
    public boolean expiresWithin(long seconds) { return Instant.now().plusSeconds(seconds).isAfter(expiresAt); }
}
