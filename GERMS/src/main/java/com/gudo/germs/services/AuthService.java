package com.gudo.germs.services;

import com.gudo.germs.models.Profile;
import com.gudo.germs.repositories.ProfileRepository;
import com.gudo.germs.supabase.AuthClient;
import com.gudo.germs.supabase.Session;
import com.gudo.germs.supabase.SessionManager;
import com.gudo.germs.supabase.SupabaseException;

/** Sign-in/out and the current user. Role comes from the server-side profile, never from the client. */
public final class AuthService {
    private final AuthClient authClient;
    private final SessionManager sessions;
    private final ProfileRepository profiles;
    private volatile Profile current;

    public AuthService(AuthClient authClient, SessionManager sessions, ProfileRepository profiles) {
        this.authClient = authClient;
        this.sessions = sessions;
        this.profiles = profiles;
    }

    /** Blocking; call from a background thread. */
    public Profile login(String email, String password) throws SupabaseException {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new SupabaseException(400, "Enter your email and password.");
        }
        Session s = authClient.signIn(email.trim(), password);
        sessions.set(s);
        Profile p = profiles.findById(s.userId()).orElse(null);
        if (p == null || !p.active()) {
            logout();
            throw new SupabaseException(403, "This account has no active GERMS profile. Contact headquarters.");
        }
        current = p;
        return p;
    }

    public void logout() {
        String token = sessions.current().map(Session::accessToken).orElse(null);
        sessions.clear();
        current = null;
        if (token != null) {
            try { authClient.signOut(token); } catch (SupabaseException ignored) { /* local session already cleared */ }
        }
    }

    public Profile currentProfile() { return current == null ? Profile.guest() : current; }
}
