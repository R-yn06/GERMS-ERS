package com.gudo.germs.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class IncidentBuilder {
    private String id;
    private String title;
    private String type = "Other";
    private String location;
    private String severity = "moderate";
    private IncidentStatus status = IncidentStatus.DRAFT;
    private String team;
    private String reportedBy;
    private Instant reportedAt;
    private final List<TimelineEntry> timeline = new ArrayList<>();

    public IncidentBuilder id(String v) { this.id = v; return this; }
    public IncidentBuilder title(String v) { this.title = v; return this; }
    public IncidentBuilder type(String v) { this.type = v; return this; }
    public IncidentBuilder location(String v) { this.location = v; return this; }
    public IncidentBuilder severity(String v) { this.severity = v; return this; }
    public IncidentBuilder status(IncidentStatus v) { this.status = v; return this; }
    public IncidentBuilder team(String v) { this.team = v; return this; }
    public IncidentBuilder reportedBy(String v) { this.reportedBy = v; return this; }
    public IncidentBuilder reportedAt(Instant v) { this.reportedAt = v; return this; }

    /** Adds an audit-trail line stamped at reportedAt (or now if not yet set). */
    public IncidentBuilder note(String actor, String text) {
        timeline.add(new TimelineEntry(reportedAt != null ? reportedAt : Instant.now(), actor, text));
        return this;
    }

    public Incident build() {
        if (title == null || title.isBlank() || location == null || location.isBlank())
            throw new IllegalArgumentException("Title and location are required.");
        if (reportedBy == null || reportedBy.isBlank()) throw new IllegalArgumentException("Reporter is required.");
        Instant at = reportedAt != null ? reportedAt : Instant.now();
        List<TimelineEntry> tl = timeline.isEmpty()
                ? List.of(new TimelineEntry(at, reportedBy, "Incident reported"))
                : List.copyOf(timeline);
        return new Incident(id, title.trim(), type, location.trim(), severity, status, team, reportedBy, at, tl);
    }
}
