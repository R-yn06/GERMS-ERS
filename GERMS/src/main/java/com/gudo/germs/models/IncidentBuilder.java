package com.gudo.germs.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds immutable incident snapshots from editable incident data. */
public final class IncidentBuilder {
    private String id;
    private String incidentNo;
    private String title;
    private String type = "Other";
    private String location;
    private String severity = "moderate";
    private IncidentStatus status = IncidentStatus.DRAFT;
    private String team;
    private String reportedBy;
    private Instant reportedAt;
    private final List<TimelineEntry> timeline = new ArrayList<>();
    private Double latitude;
    private Double longitude;
    private String description = "";

    /** Sets the incident id and returns this builder for chaining. */
    public IncidentBuilder id(String v) { this.id = v; return this; }
    /** Sets the incident incident no and returns this builder for chaining. */
    public IncidentBuilder incidentNo(String v) { this.incidentNo = v; return this; }
    /** Sets the incident title and returns this builder for chaining. */
    public IncidentBuilder title(String v) { this.title = v; return this; }
    /** Sets the incident type and returns this builder for chaining. */
    public IncidentBuilder type(String v) { this.type = v; return this; }
    /** Sets the incident location and returns this builder for chaining. */
    public IncidentBuilder location(String v) { this.location = v; return this; }
    /** Sets the incident severity and returns this builder for chaining. */
    public IncidentBuilder severity(String v) { this.severity = v; return this; }
    /** Sets the incident status and returns this builder for chaining. */
    public IncidentBuilder status(IncidentStatus v) { this.status = v; return this; }
    /** Sets the incident team and returns this builder for chaining. */
    public IncidentBuilder team(String v) { this.team = v; return this; }
    /** Sets the incident reporter and returns this builder for chaining. */
    public IncidentBuilder reportedBy(String v) { this.reportedBy = v; return this; }
    /** Sets the incident report time and returns this builder for chaining. */
    public IncidentBuilder reportedAt(Instant v) { this.reportedAt = v; return this; }
    /** Sets the incident latitude and returns this builder for chaining. */
    public IncidentBuilder latitude(Double v) { this.latitude = v; return this; }
    /** Sets the incident longitude and returns this builder for chaining. */
    public IncidentBuilder longitude(Double v) { this.longitude = v; return this; }
    /** Sets the incident description and returns this builder for chaining. */
    public IncidentBuilder description(String v) { this.description = v; return this; }

    /** Adds an audit-trail line stamped at reportedAt (or now if not yet set). */
    public IncidentBuilder note(String actor, String text) {
        timeline.add(new TimelineEntry(reportedAt != null ? reportedAt : Instant.now(), actor, text));
        return this;
    }

    /** Validates required incident fields and creates an immutable incident snapshot. */
    public Incident build() {
        if (title == null || title.isBlank() || location == null || location.isBlank())
            throw new IllegalArgumentException("Title and location are required.");
        if (reportedBy == null || reportedBy.isBlank()) throw new IllegalArgumentException("Reporter is required.");
        Instant at = reportedAt != null ? reportedAt : Instant.now();
        List<TimelineEntry> tl = timeline.isEmpty()
                ? List.of(new TimelineEntry(at, reportedBy, "Incident reported"))
                : List.copyOf(timeline);
        return new Incident(id, incidentNo, title.trim(), type, location.trim(), severity, status, team, reportedBy, at, tl, latitude, longitude, description);
    }
}
