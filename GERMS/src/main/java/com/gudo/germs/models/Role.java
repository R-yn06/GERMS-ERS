package com.gudo.germs.models;

public enum Role {
    ADMIN("Administrator"), RESPONDER("Responder"), GUEST("Guest");

    private final String label;
    Role(String label) { this.label = label; }
    public String label() { return label; }

    public static Role fromDb(String value) {
        return switch (value == null ? "" : value) {
            case "admin" -> ADMIN;
            case "responder" -> RESPONDER;
            default -> throw new IllegalArgumentException("Unknown role: " + value);
        };
    }
}
