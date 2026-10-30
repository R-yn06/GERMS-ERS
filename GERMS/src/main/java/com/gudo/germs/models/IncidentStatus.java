package com.gudo.germs.models;

import java.util.Optional;

/**
 * STATE PATTERN (enum-based): each lifecycle state knows its legal next state and any rule
 * that must hold before entering it. The UI and service never hard-code the transition table.
 */
public enum IncidentStatus {
    DRAFT("draft", "Draft"),
    PUBLISHED("published", "Published"),
    ACKNOWLEDGED("acknowledged", "Acknowledged"),
    MOBILIZING("mobilizing", "Mobilizing") {
        @Override public String blocker(Incident i) {
            return i.team() == null ? "Assign a response team before mobilizing." : null;
        }
    },
    ACTIVE_RESPONSE("active_response", "Active response"),
    STABILIZED("stabilized", "Stabilized"),
    CLOSED("closed", "Closed"),
    ARCHIVED("archived", "Archived");

    private final String key;
    private final String label;

    IncidentStatus(String key, String label) { this.key = key; this.label = label; }

    public String key() { return key; }
    public String label() { return label; }

    /** The single legal forward transition from this state, if any. */
    public Optional<IncidentStatus> next() {
        return switch (this) {
            case DRAFT -> Optional.of(PUBLISHED);
            case PUBLISHED -> Optional.of(ACKNOWLEDGED);
            case ACKNOWLEDGED -> Optional.of(MOBILIZING);
            case MOBILIZING -> Optional.of(ACTIVE_RESPONSE);
            case ACTIVE_RESPONSE -> Optional.of(STABILIZED);
            case STABILIZED -> Optional.of(CLOSED);
            case CLOSED, ARCHIVED -> Optional.empty();
        };
    }

    /** Returns a human-readable reason the incident may NOT enter this state, or null if allowed. */
    public String blocker(Incident incident) { return null; }

    /** Same definition of "active" the dashboard already used. */
    public boolean isActive() {
        return this == PUBLISHED || this == ACKNOWLEDGED || this == MOBILIZING
                || this == ACTIVE_RESPONSE || this == STABILIZED;
    }

    public boolean isTerminal() { return next().isEmpty(); }
}
