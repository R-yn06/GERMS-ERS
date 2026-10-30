package com.gudo.germs.services;

import com.gudo.germs.models.Incident;

public record IncidentEvent(Type type, Incident incident) {
    public enum Type { CREATED, UPDATED }
}
