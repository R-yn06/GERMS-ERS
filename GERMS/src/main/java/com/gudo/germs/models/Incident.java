package com.gudo.germs.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Immutable incident snapshot (safe to pass between the background thread and the EDT). */
public record Incident(String id, String title, String type, String location, String severity,
                       IncidentStatus status, String team, String reportedBy, Instant reportedAt,
                       List<TimelineEntry> timeline) {

    public Incident withId(String newId) {
        return new Incident(newId, title, type, location, severity, status, team, reportedBy, reportedAt, timeline);
    }

    public Incident withStatus(IncidentStatus s, TimelineEntry entry) {
        return new Incident(id, title, type, location, severity, s, team, reportedBy, reportedAt, plus(entry));
    }

    public Incident withTeam(String t, TimelineEntry entry) {
        return new Incident(id, title, type, location, severity, status, t, reportedBy, reportedAt, plus(entry));
    }

    private List<TimelineEntry> plus(TimelineEntry e) {
        List<TimelineEntry> copy = new ArrayList<>(timeline);
        copy.add(e);
        return List.copyOf(copy);
    }
}
