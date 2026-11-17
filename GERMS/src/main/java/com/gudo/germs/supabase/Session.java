package com.gudo.germs.supabase;

import java.time.Instant;
import java.util.UUID;

/** Stores the access and refresh tokens and their expiry for an authenticated user. */
public record Session(String accessToken, String refreshToken, Instant expiresAt, UUID userId, String email) {
    /** Reports whether this session expires within the supplied interval. */
    public boolean expiresWithin(long seconds) { return Instant.now().plusSeconds(seconds).isAfter(expiresAt); }
}
