package com.gudo.germs.supabase;

import java.util.Optional;

/** Holds the signed-in session in memory only and refreshes the access token shortly before it expires. */
public final class SessionManager {
    private final AuthClient auth;
    private Session session;

    public SessionManager(AuthClient auth) { this.auth = auth; }

    public synchronized void set(Session s) { this.session = s; }
    public synchronized void clear() { this.session = null; }
    public synchronized Optional<Session> current() { return Optional.ofNullable(session); }

    /** @return a valid JWT, or null when nobody is signed in (anonymous/guest requests). */
    public synchronized String accessToken() throws SupabaseException {
        if (session == null) return null;
        if (session.expiresWithin(60)) {
            session = auth.refresh(session.refreshToken());
        }
        return session.accessToken();
    }
}
