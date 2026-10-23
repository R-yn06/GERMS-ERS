package com.gudo.germs.supabase;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** PostgREST access. Row Level Security applies because every call carries the user's JWT. */
public final class RestClient {
    private final SupabaseHttp http;
    private final SessionManager sessions;

    public RestClient(SupabaseHttp http, SessionManager sessions) {
        this.http = http;
        this.sessions = sessions;
    }

    /** @param query PostgREST query string without the leading "?", e.g. "select=*&status=eq.published" */
    public JsonNode select(String table, String query) throws SupabaseException {
        return http.send("GET", "/rest/v1/" + table + (query == null || query.isEmpty() ? "" : "?" + query),
                null, sessions.accessToken(), null);
    }

    public JsonNode rpc(String function, Object args) throws SupabaseException {
        String body = args == null ? "{}" : SupabaseHttp.JSON.valueToTree(args).toString();
        return http.send("POST", "/rest/v1/rpc/" + function, body, sessions.accessToken(), null);
    }

    /** Insert without reading the row back (guests may insert but never select). */
    public void insertMinimal(String table, Object row) throws SupabaseException {
        http.send("POST", "/rest/v1/" + table, SupabaseHttp.JSON.valueToTree(row).toString(),
                sessions.accessToken(), Map.of("Prefer", "return=minimal"));
    }
}
