package com.gudo.germs.models;

public enum Role {
    ADMIN("Administrator"), RESPONDER("Responder"), GUEST("Guest");

    private final String label;
    Role(String label) { this.label = label; }
    public String label() { return label; }

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
