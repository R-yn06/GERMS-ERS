package com.gudo.germs.security;

import com.gudo.germs.models.Role;

import java.util.EnumSet;
import java.util.Set;

/**
 * Every operational module in GERMS, its description, and authorized roles.
 * Fully aligned with GERMS Phase 3 master directive (Section 21).
 */
public enum Module {
    // --- Administrator & Shared Operational Modules ---
    DASHBOARD("Command Dashboard", 2, "Operational overview and live status metrics.", Role.ADMIN, Role.RESPONDER),
    INCIDENTS("Incident Management", 2, "Incident lifecycle, verification, and command.", Role.ADMIN, Role.RESPONDER),
    DISPATCH("Dispatch Center", 2, "Responder dispatching, deployments, and response coordination.", Role.ADMIN),
    MAP("Live Operations Map", 3, "Interactive OpenStreetMap view of incidents, responders, and resources.", Role.ADMIN, Role.RESPONDER),
    USERS("User & Responder Management", 2, "Account management, role promotion, classifications, and availability.", Role.ADMIN),
    TEAMS("Team & Squad Management", 3, "Responder teams, team leaders, squad rosters, and deployments.", Role.ADMIN),
    RESOURCES("Resource & Equipment", 3, "Emergency resource inventory, allocations, and condition tracking.", Role.ADMIN),
    EVACUATION("Evacuation Centers", 2, "Capacity, occupancy, operating status, and location coordination.", Role.ADMIN),
    FIELD_REPORTS("Field Situation Reports", 3, "Field situation updates, casualty reports, and resource needs.", Role.ADMIN, Role.RESPONDER),
    COMMUNICATION("Emergency Communication", 3, "Incident-specific messaging, announcements, and team channels.", Role.ADMIN, Role.RESPONDER),
    CONTACTS("Emergency Contacts", 3, "Directory of hotlines, hospitals, LGUs, and rescue partners.", Role.ADMIN),
    REPORTS("Reports & Analytics", 3, "Operational incident analytics, trends, and data exports.", Role.ADMIN),
    AUDIT("Activity & Audit Logs", 2, "Append-only chronological audit trail of all command actions.", Role.ADMIN),
    SETTINGS("System Settings", 1, "Theme, audio alarm alerts, and operational preferences.", Role.ADMIN, Role.RESPONDER, Role.GUEST),

    // --- Dedicated Responder Modules ---
    MY_DEPLOYMENTS("My Deployments", 3, "Current incident assignments, status transitions, notifications, alarms, and history.", Role.RESPONDER),
    MY_TEAM("My Assigned Team", 3, "Assigned squad, team leader, members, and team status.", Role.RESPONDER),

    // --- Guest / Public Experience Modules ---
    GUEST_ADVISORIES("Public Announcements", 2, "Official public safety advisories and weather warnings.", Role.GUEST),
    GUEST_REPORT("Submit Incident Report", 2, "Report emergencies directly to Red Cross dispatchers.", Role.GUEST),
    GUEST_MY_REPORTS("My Submitted Reports", 3, "Track the verification and response status of your reports.", Role.GUEST),
    GUEST_EVACUATION("Evacuation Centers", 2, "Public evacuation shelter capacities and availability.", Role.GUEST),
    GUEST_CONTACTS("Emergency Contacts", 2, "Official emergency hotlines and medical facility directory.", Role.GUEST);

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
