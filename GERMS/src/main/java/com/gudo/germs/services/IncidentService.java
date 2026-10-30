package com.gudo.germs.services;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentBuilder;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.ResponseTeam;
import com.gudo.germs.supabase.SupabaseException;

import java.util.List;
import java.util.UUID;

/**
 * Application logic for the main workflow: report -> publish -> assign -> respond -> close.
 * Shared through AppContext so every screen uses one repository and event bus.
 * Safe to call from a background thread (see BackgroundTask).
 */
public final class IncidentService {
    private final IncidentRepository repo;
    private final IncidentBus bus = new IncidentBus();

    public IncidentService(IncidentRepository repo) { this.repo = repo; }

    public IncidentBus bus() { return bus; }

    /** Guest visibility is restricted by Supabase's public advisories view; staff visibility is RLS-filtered. */
    public List<Incident> visibleTo(boolean guest) throws SupabaseException {
        return guest ? repo.findPublic() : repo.findAll();
    }

    public List<ResponseTeam> responseTeams() throws SupabaseException {
        return repo.findTeams();
    }

    public synchronized Incident report(String title, String type, String location, String severity, String reporter)
            throws SupabaseException {
        Incident built = new IncidentBuilder()
                .id(UUID.randomUUID().toString())
                .title(title).type(type).location(location).severity(severity)
                .reportedBy(reporter)
                .note(reporter, "Incident reported; awaiting verification")
                .build();
        Incident i = repo.create(built);
        bus.publish(new IncidentEvent(IncidentEvent.Type.CREATED, i));
        return i;
    }

    public synchronized Incident submitGuestReport(String title, String type, String location, String severity, String reporter)
            throws SupabaseException {
        Incident report = new IncidentBuilder()
                .id(UUID.randomUUID().toString())
                .title(title).type(type).location(location).severity(severity)
                .reportedBy(reporter)
                .status(IncidentStatus.DRAFT)
                .note(reporter, "Preliminary report submitted for verification")
                .build();
        Incident saved = repo.submitGuestReport(report);
        bus.publish(new IncidentEvent(IncidentEvent.Type.CREATED, saved));
        return saved;
    }

    public synchronized Incident assignTeam(String id, ResponseTeam team) throws SupabaseException {
        Incident cur = get(id);
        if (cur.status().isTerminal()) throw new IllegalStateException("Incident is " + cur.status().label() + ".");
        Incident updated = repo.assignTeam(id, team);
        bus.publish(new IncidentEvent(IncidentEvent.Type.UPDATED, updated));
        return updated;
    }

    public synchronized Incident advance(String id, String actor, String closureSummary) throws SupabaseException {
        Incident cur = get(id);
        IncidentStatus next = cur.status().next().orElseThrow(
                () -> new IllegalStateException("Incident is already " + cur.status().label() + "."));
        String blocker = next.blocker(cur);
        if (blocker != null) throw new IllegalStateException(blocker);
        String note;
        if (next == IncidentStatus.CLOSED) {
            if (closureSummary == null || closureSummary.isBlank()) {
                throw new IllegalArgumentException("A closure summary is required.");
            }
            note = closureSummary.trim();
        } else {
            note = "Status: " + cur.status().label() + " \u2192 " + next.label() + " (by " + actor + ")";
        }
        Incident updated = repo.advance(id, next, note);
        bus.publish(new IncidentEvent(IncidentEvent.Type.UPDATED, updated));
        return updated;
    }

    private Incident get(String id) throws SupabaseException {
        return repo.find(id).orElseThrow(() -> new IllegalArgumentException("Incident not found: " + id));
    }
}
