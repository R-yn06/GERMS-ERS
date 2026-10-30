package com.gudo.germs.services;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentBuilder;
import com.gudo.germs.models.IncidentStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Thread-safe store seeded with prepared sample records (built with IncidentBuilder). */
public final class InMemoryIncidentRepository implements IncidentRepository {
    private final Map<String, Incident> data = new LinkedHashMap<>();
    private int counter;

    public InMemoryIncidentRepository() {
        Instant now = Instant.now();
        seed("Flooding along Matina River", "Flood", "Brgy. Matina Pangi, Davao City", "high",
                IncidentStatus.PUBLISHED, null, now.minus(3, ChronoUnit.HOURS));
        seed("Residential fire, 2 houses", "Fire", "Brgy. Toril Poblacion, Davao City", "critical",
                IncidentStatus.MOBILIZING, "Rescue Team Alpha", now.minus(90, ChronoUnit.MINUTES));
        seed("Road collision near Bunawan", "Vehicular accident", "Bunawan, Davao City", "moderate",
                IncidentStatus.CLOSED, "Medical Team Bravo", now.minus(1, ChronoUnit.DAYS));
    }

    private void seed(String title, String type, String loc, String sev, IncidentStatus st, String team, Instant at) {
        Incident i = new IncidentBuilder()
                .id(nextId()).title(title).type(type).location(loc).severity(sev)
                .status(st).team(team).reportedBy("Dispatcher Reyes").reportedAt(at)
                .note("Dispatcher Reyes", "Incident reported (sample record)")
                .build();
        data.put(i.id(), i);
    }

    @Override public synchronized List<Incident> findAll() {
        List<Incident> all = new ArrayList<>(data.values());
        all.sort((a, b) -> b.reportedAt().compareTo(a.reportedAt()));
        return all;
    }

    @Override public synchronized Optional<Incident> find(String id) { return Optional.ofNullable(data.get(id)); }

    @Override public synchronized Incident save(Incident i) { data.put(i.id(), i); return i; }

    @Override public synchronized String nextId() { return String.format("INC-%03d", ++counter); }
}
