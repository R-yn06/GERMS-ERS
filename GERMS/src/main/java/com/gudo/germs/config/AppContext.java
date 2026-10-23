package com.gudo.germs.config;

import com.gudo.germs.repositories.ProfileRepository;
import com.gudo.germs.services.AuthService;
import com.gudo.germs.supabase.AuthClient;
import com.gudo.germs.supabase.RestClient;
import com.gudo.germs.supabase.SessionManager;
import com.gudo.germs.supabase.SupabaseHttp;

/** Wires the application's singletons by hand; no DI framework. */
public final class AppContext {
    public final AppConfig config;
    public final SessionManager sessions;
    public final RestClient rest;
    public final AuthService auth;

    private AppContext(AppConfig config) {
        this.config = config;
        SupabaseHttp http = new SupabaseHttp(config);
        this.sessions = new SessionManager(new AuthClient(http));
        this.rest = new RestClient(http, sessions);
        this.auth = new AuthService(new AuthClient(http), sessions, new ProfileRepository(rest));
    }

    public static AppContext create() { return new AppContext(AppConfig.load()); }
}
