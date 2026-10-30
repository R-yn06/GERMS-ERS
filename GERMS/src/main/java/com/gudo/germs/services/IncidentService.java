package com.gudo.germs.services;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentBuilder;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.TimelineEntry;

import java.time.Instant;
import java.util.List;

/**
 * Application logic for the main workflow: report -> publish -> assign -> respond -> close.
 * SINGLETON so every screen shares one repository and one event bus.
 * Safe to call from a background thread (see BackgroundTask).
 */
public final class IncidentService {
    public static final List<String> TEAMS = List.of(
            "Rescue Team Alpha", "Medical Team Bravo", "Logistics Team Charlie", "Water Rescue Team Delta");

    private static final IncidentService INSTANCE = new IncidentService(new InMemoryIncidentRepository());

    public static IncidentService get() { return INSTANCE; }

    private final IncidentRepository repo;
    private final IncidentBus bus = new IncidentBus();

    /** Private: the only instance is INSTANCE, so every screen shares one repository and one event bus. */
    private IncidentService(IncidentRepository repo) 
    { 
        this.repo = repo; 

    }

    public IncidentBus bus() { return bus; }

    /** Guests only see incidents that were verified and published; staff see everything. */
    public List<Incident> visibleTo(boolean guest) {
        return repo.findAll().stream().filter(i -> !guest || i.status() != IncidentStatus.DRAFT).toList();
    }

    public synchronized Incident report(String title, String type, String location, String severity, String reporter) {
        // Builder validates first, so a rejected form never consumes an incident number.
        Incident built = new IncidentBuilder()
                .title(title).type(type).location(location).severity(severity)
                .reportedBy(reporter)
                .note(reporter, "Incident reported; awaiting verification")
                .build();
        Incident i = repo.save(built.withId(repo.nextId()));
        bus.publish(new IncidentEvent(IncidentEvent.Type.CREATED, i));
        return i;
    }

    public synchronized Incident assignTeam(String id, String team, String actor) {
        Incident cur = get(id);
        if (cur.status().isTerminal()) throw new IllegalStateException("Incident is " + cur.status().label() + ".");
        if (!TEAMS.contains(team)) throw new IllegalArgumentException("Unknown team.");
        Incident updated = repo.save(cur.withTeam(team,
                new TimelineEntry(Instant.now(), actor, "Assigned to " + team)));
        bus.publish(new IncidentEvent(IncidentEvent.Type.UPDATED, updated));
        return updated;
    }

    public synchronized Incident advance(String id, String actor) {
        Incident cur = get(id);
        IncidentStatus next = cur.status().next().orElseThrow(
                () -> new IllegalStateException("Incident is already " + cur.status().label() + "."));
        String blocker = next.blocker(cur);
        if (blocker != null) throw new IllegalStateException(blocker);
        Incident updated = repo.save(cur.withStatus(next, new TimelineEntry(Instant.now(), actor,
                "Status: " + cur.status().label() + " \u2192 " + next.label())));
        bus.publish(new IncidentEvent(IncidentEvent.Type.UPDATED, updated));
        return updated;
    }

    private Incident get(String id) {
        return repo.find(id).orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
