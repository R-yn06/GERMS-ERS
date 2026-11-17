package com.gudo.germs.services;

import com.gudo.germs.models.Incident;

/** Carries an incident update to interested application components. */
public record IncidentEvent(Type type, Incident incident) {
    /** Identifies the kind of incident event being published. */
    public enum Type { CREATED, UPDATED }
}
