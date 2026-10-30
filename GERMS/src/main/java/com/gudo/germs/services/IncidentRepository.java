package com.gudo.germs.services;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.ResponseTeam;
import com.gudo.germs.supabase.SupabaseException;

import java.util.List;
import java.util.Optional;

/** Supabase-backed incident persistence boundary. */
public interface IncidentRepository {
    List<Incident> findAll() throws SupabaseException;
    List<Incident> findPublic() throws SupabaseException;
    Optional<Incident> find(String id) throws SupabaseException;
    List<ResponseTeam> findTeams() throws SupabaseException;
    Incident create(Incident incident) throws SupabaseException;
    Incident submitGuestReport(Incident report) throws SupabaseException;
    Incident assignTeam(String id, ResponseTeam team) throws SupabaseException;
    Incident advance(String id, IncidentStatus status, String note) throws SupabaseException;
}
