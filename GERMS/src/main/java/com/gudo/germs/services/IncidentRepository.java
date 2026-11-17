package com.gudo.germs.services;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.ResponseTeam;
import com.gudo.germs.supabase.SupabaseException;

import java.util.List;
import java.util.Optional;

/** Supabase-backed incident persistence boundary. */
public interface IncidentRepository {
    /** Finds all. */
    List<Incident> findAll() throws SupabaseException;
    /** Finds public. */
    List<Incident> findPublic() throws SupabaseException;
    /** Finds an incident by ID. */
    Optional<Incident> find(String id) throws SupabaseException;
    /** Finds teams. */
    List<ResponseTeam> findTeams() throws SupabaseException;
    /** Creates and persists an incident. */
    Incident create(Incident incident) throws SupabaseException;
    /** Submits a guest report for staff review. */
    Incident submitGuestReport(Incident report) throws SupabaseException;
    /** Updates severity. */
    Incident updateSeverity(String id, String severity) throws SupabaseException;
    /** Assigns a response team to an incident. */
    Incident assignTeam(String id, ResponseTeam team) throws SupabaseException;
    /** Advances an incident to the supplied workflow status. */
    Incident advance(String id, IncidentStatus status, String note) throws SupabaseException;
    /** Records the response decision and note for an incident. */
    void respond(String id, String decision, String note) throws SupabaseException;
}
