package com.gudo.germs.utils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.prefs.Preferences;

/** Provides a persistent local bearer token for tracking anonymous public reports. */
public final class GuestReportTracking {
    private static final String PREFERENCE_KEY = "guest_report_tracking_token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private GuestReportTracking() { }

    /** Returns the token used to securely retrieve reports submitted from this guest installation. */
    public static synchronized String token() {
        Preferences preferences = Preferences.userNodeForPackage(GuestReportTracking.class);
        String token = preferences.get(PREFERENCE_KEY, null);
        if (token == null || token.isBlank()) {
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            preferences.put(PREFERENCE_KEY, token);
        }
        return token;
    }
}
