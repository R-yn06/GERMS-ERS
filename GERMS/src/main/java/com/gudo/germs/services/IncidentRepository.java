package com.gudo.germs.services;

import com.gudo.germs.models.Incident;

import java.util.List;
import java.util.Optional;

/** REPOSITORY PATTERN: the service depends on this, not on where data lives (memory now, Supabase later). */
public interface IncidentRepository {
    List<Incident> findAll();
    Optional<Incident> find(String id);
    Incident save(Incident incident);
    String nextId();
}
