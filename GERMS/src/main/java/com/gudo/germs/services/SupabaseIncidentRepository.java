package com.gudo.germs.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.ResponseTeam;
import com.gudo.germs.models.TimelineEntry;
import com.gudo.germs.supabase.RestClient;
import com.gudo.germs.supabase.SupabaseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Incident and response-team persistence through Supabase PostgREST and secured RPCs. */
public final class SupabaseIncidentRepository implements IncidentRepository {
    private static final String INCIDENT_FIELDS =
            "id,incident_no,title,category,severity,status,reported_at,location_text,"
                    + "creator:profiles!incidents_created_by_fkey(full_name),response_teams(name),"
                    + "incident_updates(id,created_at,message,author_id,"
                    + "profiles!incident_updates_author_id_fkey(full_name))";

    private final RestClient rest;

    public SupabaseIncidentRepository(RestClient rest) { this.rest = rest; }

    @Override public List<Incident> findAll() throws SupabaseException {
        JsonNode rows = rest.select("incidents", "select=" + INCIDENT_FIELDS
                + "&order=reported_at.desc&incident_updates.order=created_at.asc");
        List<Incident> incidents = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) incidents.add(toIncident(row));
        }
        return List.copyOf(incidents);
    }

    @Override public List<Incident> findPublic() throws SupabaseException {
        JsonNode rows = rest.select("public_advisories",
                "select=id,incident_no,title,category,severity,status,barangay,district,city,public_summary,published_at"
                        + "&order=published_at.desc");
        List<Incident> incidents = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                Instant publishedAt = Instant.parse(row.path("published_at").asText());
                String location = String.join(", ", List.of(
                        row.path("barangay").asText(""),
                        row.path("district").asText(""),
                        row.path("city").asText("")).stream().filter(value -> !value.isBlank()).toList());
                String summary = row.path("public_summary").asText("");
                incidents.add(new Incident(
                        row.path("id").asText(),
                        row.path("incident_no").asText(),
                        row.path("title").asText(),
                        categoryLabel(row.path("category").asText("other")),
                        location,
                        row.path("severity").asText("moderate"),
                        IncidentStatus.fromKey(row.path("status").asText()),
                        null,
                        "GERMS",
                        publishedAt,
                        List.of(new TimelineEntry(publishedAt, "GERMS",
                                summary.isBlank() ? "Public incident advisory" : summary))));
            }
        }
        return List.copyOf(incidents);
    }

    @Override public Optional<Incident> find(String id) throws SupabaseException {
        String uuid = UUID.fromString(id).toString();
        JsonNode rows = rest.select("incidents", "select=" + INCIDENT_FIELDS + "&id=eq." + uuid);
        return rows.isArray() && !rows.isEmpty() ? Optional.of(toIncident(rows.get(0))) : Optional.empty();
    }

    @Override public List<ResponseTeam> findTeams() throws SupabaseException {
        JsonNode rows = rest.select("response_teams", "select=id,name&is_active=eq.true&order=name.asc");
        List<ResponseTeam> teams = new ArrayList<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) teams.add(ResponseTeam.fromJson(row));
        }
        return List.copyOf(teams);
    }

    @Override public Incident create(Incident incident) throws SupabaseException {
        Map<String, Object> values = Map.of(
                "title", incident.title(),
                "category", categoryCode(incident.type()),
                "severity", incident.severity(),
                "location_text", incident.location(),
                "is_public", false);
        JsonNode result = rest.rpc("create_incident", Map.of("p", values));
        String id = result.isArray() && !result.isEmpty() ? result.get(0).asText() : result.asText();
        return find(id).orElseThrow(() -> new SupabaseException(404, "The created incident could not be loaded."));
    }

    @Override public Incident submitGuestReport(Incident report) throws SupabaseException {
        String description = report.title() + "\nType: " + report.type() + "\nSeverity: " + report.severity();
        if (description.length() > 2000) {
            throw new IllegalArgumentException("The report must be no longer than 2,000 characters.");
        }
        if (report.location().length() > 300) {
            throw new IllegalArgumentException("The location must be no longer than 300 characters.");
        }
        if (report.reportedBy().length() > 120) {
            throw new IllegalArgumentException("The reporter name must be no longer than 120 characters.");
        }
        rest.insertMinimal("guest_reports", Map.of(
                "id", report.id(),
                "reporter_name", report.reportedBy(),
                "description", description,
                "location_text", report.location()));
        return report.withIncidentNo("REPORT-" + report.id().substring(0, 8).toUpperCase());
    }

    @Override public Incident assignTeam(String id, ResponseTeam team) throws SupabaseException {
        rest.rpc("assign_incident_team", Map.of(
                "p_id", UUID.fromString(id).toString(),
                "p_team", team.id().toString()));
        return find(id).orElseThrow(() -> new SupabaseException(404, "The updated incident could not be loaded."));
    }

    @Override public Incident advance(String id, IncidentStatus status, String note) throws SupabaseException {
        String uuid = UUID.fromString(id).toString();
        if (status == IncidentStatus.PUBLISHED) {
            rest.rpc("publish_incident", Map.of("p_id", uuid));
        } else {
            rest.rpc("set_incident_status", Map.of(
                    "p_id", uuid,
                    "p_to", status.key(),
                    "p_note", note));
        }
        return find(id).orElseThrow(() -> new SupabaseException(404, "The updated incident could not be loaded."));
    }

    private static Incident toIncident(JsonNode row) {
        String id = row.path("id").asText();
        Instant reportedAt = Instant.parse(row.path("reported_at").asText());
        String creator = nestedName(row.path("creator"), "GERMS user");
        JsonNode responseTeam = row.path("response_teams");
        String team = responseTeam.isArray()
                ? (responseTeam.isEmpty() ? null : responseTeam.get(0).path("name").asText(null))
                : responseTeam.path("name").asText(null);

        List<TimelineEntry> timeline = new ArrayList<>();
        JsonNode updates = row.path("incident_updates");
        if (updates.isArray()) {
            for (JsonNode update : updates) {
                String text = update.path("message").asText("");
                if (text.isBlank()) {
                    String from = update.path("from_status").asText("");
                    String to = update.path("to_status").asText("");
                    text = from.isBlank() ? "Incident update" : "Status: " + from + " -> " + to;
                }
                timeline.add(new TimelineEntry(
                        Instant.parse(update.path("created_at").asText()),
                        nestedName(update.path("profiles"), "GERMS user"),
                        text));
            }
        }
        if (timeline.isEmpty()) {
            timeline.add(new TimelineEntry(reportedAt, creator, "Incident reported"));
        }

        return new Incident(
                id,
                row.path("incident_no").asText(id),
                row.path("title").asText(),
                categoryLabel(row.path("category").asText("other")),
                row.path("location_text").asText(""),
                row.path("severity").asText("moderate"),
                IncidentStatus.fromKey(row.path("status").asText()),
                team,
                creator,
                reportedAt,
                timeline);
    }

    private static String nestedName(JsonNode relation, String fallback) {
        if (relation.isArray()) {
            return relation.isEmpty() ? fallback : relation.get(0).path("full_name").asText(fallback);
        }
        return relation.path("full_name").asText(fallback);
    }

    private static String categoryCode(String type) {
        String normalized = type.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "flood", "typhoon", "fire", "landslide", "vehicular_accident", "medical_emergency",
                    "search_and_rescue", "mass_casualty", "evacuation", "relief_operation", "other" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported incident category: " + type);
        };
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "vehicular_accident" -> "Vehicular accident";
            case "medical_emergency" -> "Medical emergency";
            case "search_and_rescue" -> "Search and rescue";
            case "mass_casualty" -> "Mass casualty";
            case "relief_operation" -> "Relief operation";
            default -> category.substring(0, 1).toUpperCase() + category.substring(1).replace('_', ' ');
        };
    }
}
