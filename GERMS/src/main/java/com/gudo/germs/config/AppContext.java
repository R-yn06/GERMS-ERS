package com.gudo.germs.config;

import com.gudo.germs.repositories.ProfileRepository;
import com.gudo.germs.services.AuthService;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.services.SupabaseIncidentRepository;
import com.gudo.germs.supabase.AuthClient;
import com.gudo.germs.supabase.RestClient;
import com.gudo.germs.supabase.SessionManager;
import com.gudo.germs.supabase.SupabaseHttp;


public final class AppContext {
    private static AppContext instance;

    public final AppConfig config;
    public final SessionManager sessions;
    public final RestClient rest;
    public final AuthService auth;
    public final IncidentService incidents;

    private AppContext(AppConfig config) {
        this.config = config;
        SupabaseHttp http = new SupabaseHttp(config);
        this.sessions = new SessionManager(new AuthClient(http));
        this.rest = new RestClient(http, sessions);
        this.auth = new AuthService(new AuthClient(http), sessions, new ProfileRepository(rest));
        this.incidents = new IncidentService(new SupabaseIncidentRepository(rest));
    }

    public static synchronized AppContext get() {
        if (instance == null) instance = new AppContext(AppConfig.load());
        return instance;
    }

   
    public static AppContext create() { return get(); }
}