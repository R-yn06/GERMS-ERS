package com.gudo.germs.security;

import com.gudo.germs.models.Role;

import java.util.EnumSet;
import java.util.Set;

/** Every sidebar module, who may see it, and the phase in which it is implemented. */
public enum Module {
    DASHBOARD("Dashboard", 2, "Operational overview.", Role.ADMIN, Role.RESPONDER),
    INCIDENTS("Emergency Incidents", 2, "Create, publish, and track incidents.", Role.ADMIN, Role.RESPONDER),
    MAP("Live Operations Map", 3, "OpenStreetMap view of incidents, responders, and resources.", Role.ADMIN, Role.RESPONDER),
    RESPONDERS("Responder Management", 3, "Profiles, classifications, qualifications, availability.", Role.ADMIN),
    DISPATCH("Deployment and Dispatch", 2, "Review responses and assign personnel.", Role.ADMIN),
    FLEET("Ambulance and Fleet", 4, "Vehicles, crews, and maintenance.", Role.ADMIN),
    DISASTER("Disaster Management", 4, "Relief inventory and distribution.", Role.ADMIN),
    EVACUATION("Evacuation Centers", 4, "Capacity and occupancy.", Role.ADMIN),
    BLOOD("Blood Services", 5, "Batch inventory and hospital requests.", Role.ADMIN),
    VOLUNTEERS("Volunteer Services", 4, "Barangay groups and Red Cross 143.", Role.ADMIN),
    SAFETY("Safety Services and Training", 5, "Training and certifications.", Role.ADMIN),
    WELFARE("Health and Welfare", 5, "Restricted welfare records.", Role.ADMIN),
    REPORTS("Reports and Analytics", 6, "Reports and exports.", Role.ADMIN),
    USERS("User Management", 2, "Create and manage accounts.", Role.ADMIN),
    AUDIT("Audit Logs", 2, "Append-only record of critical actions.", Role.ADMIN),
    SETTINGS("Settings", 1, "Theme and preferences.", Role.ADMIN, Role.RESPONDER, Role.GUEST),
    GUEST_ADVISORIES("Public Advisories", 2, "Approved public advisories.", Role.GUEST),
    GUEST_EVACUATION("Evacuation Centers", 4, "Public evacuation center information.", Role.GUEST),
    GUEST_CONTACTS("Emergency Contacts", 2, "Verified emergency contacts.", Role.GUEST),
    GUEST_REPORT("Report an Incident", 2, "Preliminary, unverified report.", Role.GUEST);

    private final String title;
    private final int phase;
    private final String description;
    private final Set<Role> roles;

    Module(String title, int phase, String description, Role first, Role... rest) {
        this.title = title;
        this.phase = phase;
        this.description = description;
        this.roles = EnumSet.of(first, rest);
    }

    public String title() { return title; }
    public int phase() { return phase; }
    public String description() { return description; }
    public boolean allows(Role role) { return roles.contains(role); }
}
