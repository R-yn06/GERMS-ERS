package com.gudo.germs.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Immutable incident snapshot (safe to pass between the background thread and the EDT). */
public record Incident(String id, String incidentNo, String title, String type, String location, String severity,
                       IncidentStatus status, String team, String reportedBy, Instant reportedAt,
                       List<TimelineEntry> timeline, Double latitude, Double longitude, String description) {

    /** Creates an incident instance. */
    public Incident {
        timeline = List.copyOf(timeline == null ? List.of() : timeline);
        if (description == null) description = "";
    }

    /** Creates an incident instance. */
    public Incident(String id, String incidentNo, String title, String type, String location, String severity,
                    IncidentStatus status, String team, String reportedBy, Instant reportedAt,
                    List<TimelineEntry> timeline) {
        this(id, incidentNo, title, type, location, severity, status, team, reportedBy, reportedAt, timeline, null, null, "");
    }

    /** Returns the incident number, falling back to its ID when no number is set. */
    public String reference() {
        return incidentNo == null || incidentNo.isBlank() ? id : incidentNo;
    }

    /** Returns a string representation of this value. */
    @Override public String toString() { return reference() + " \u00B7 " + title + " (" + severity.toUpperCase() + ")"; }

    /** Returns a copy of this incident with the supplied incident number. */
    public Incident withIncidentNo(String newIncidentNo) {
        return new Incident(id, newIncidentNo, title, type, location, severity, status, team, reportedBy, reportedAt, timeline, latitude, longitude, description);
    }

    /** Returns a copy of this incident with the supplied ID. */
    public Incident withId(String newId) {
        return new Incident(newId, incidentNo, title, type, location, severity, status, team, reportedBy, reportedAt, timeline, latitude, longitude, description);
    }

    /** Returns a copy with the supplied status and appended timeline entry. */
    public Incident withStatus(IncidentStatus s, TimelineEntry entry) {
        return new Incident(id, incidentNo, title, type, location, severity, s, team, reportedBy, reportedAt, plus(entry), latitude, longitude, description);
    }

    /** Returns a copy with the supplied response team and appended timeline entry. */
    public Incident withTeam(String t, TimelineEntry entry) {
        return new Incident(id, incidentNo, title, type, location, severity, status, t, reportedBy, reportedAt, plus(entry), latitude, longitude, description);
    }

    /** Returns a copy with the supplied geographic coordinates. */
    public Incident withCoordinates(Double lat, Double lon) {
        return new Incident(id, incidentNo, title, type, location, severity, status, team, reportedBy, reportedAt, timeline, lat, lon, description);
    }

    /** Returns a copy of the timeline with the supplied entry appended. */
    private List<TimelineEntry> plus(TimelineEntry e) {
        List<TimelineEntry> copy = new ArrayList<>(timeline);
        copy.add(e);
        return List.copyOf(copy);
    }
}
