package com.gudo.germs.models;

/** Defines the application roles used for access decisions. */
public enum Role {
    ADMIN("Administrator"), RESPONDER("Responder"), GUEST("Guest");

    private final String label;
    /** Creates a role instance. */
    Role(String label) { this.label = label; }
    /** Returns the display label for this role. */
    public String label() { return label; }

    /** Maps a database role name to an application role, defaulting unknown values to {@code GUEST}. */
    public static Role fromDb(String value) {
        if (value == null) return GUEST;
        return switch (value.toLowerCase().trim()) {
            case "admin" -> ADMIN;
            case "responder" -> RESPONDER;
            case "guest", "user" -> GUEST;
            default -> GUEST;
        };
    }
}
