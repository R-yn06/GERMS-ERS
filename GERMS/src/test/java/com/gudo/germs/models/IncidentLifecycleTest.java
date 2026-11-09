package com.gudo.germs.models;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncidentLifecycleTest {

    @Test
    void incidentBuilderCreatesValidIncidentWithCoordinates() {
        Incident i = new IncidentBuilder()
                .id("test-inc-1")
                .incidentNo("GERMS-2026-00001")
                .title("Flash Flood Alert")
                .type("Flood")
                .location("Matina Crossing, Davao City")
                .severity("critical")
                .latitude(7.0543)
                .longitude(125.5921)
                .description("Rapid water level rise observed near riverbanks.")
                .reportedBy("Dispatcher Alpha")
                .build();

        assertNotNull(i);
        assertEquals("GERMS-2026-00001", i.incidentNo());
        assertEquals("Flash Flood Alert", i.title());
        assertEquals("critical", i.severity());
        assertEquals(7.0543, i.latitude());
        assertEquals(125.5921, i.longitude());
        assertEquals(IncidentStatus.DRAFT, i.status());
        assertFalse(i.timeline().isEmpty());
    }

    @Test
    void incidentBuilderEnforcesMandatoryFields() {
        assertThrows(IllegalArgumentException.class, () ->
                new IncidentBuilder().title("").location("Davao").reportedBy("Staff").build());

        assertThrows(IllegalArgumentException.class, () ->
                new IncidentBuilder().title("Fire").location("").reportedBy("Staff").build());

        assertThrows(IllegalArgumentException.class, () ->
                new IncidentBuilder().title("Fire").location("Davao").reportedBy("").build());
    }

    @Test
    void incidentStatusTransitionsFollowLinearLifecycle() {
        IncidentStatus draft = IncidentStatus.DRAFT;
        IncidentStatus published = draft.next().orElseThrow();
        assertEquals(IncidentStatus.PUBLISHED, published);

        IncidentStatus acknowledged = published.next().orElseThrow();
        assertEquals(IncidentStatus.ACKNOWLEDGED, acknowledged);

        IncidentStatus mobilizing = acknowledged.next().orElseThrow();
        assertEquals(IncidentStatus.MOBILIZING, mobilizing);

        // Mobilizing blocks if no team assigned
        Incident withoutTeam = new Incident("1", "REF-1", "Fire", "Fire", "Bajada", "high", mobilizing, null, "User", Instant.now(), List.of());
        assertNotNull(mobilizing.blocker(withoutTeam));

        Incident withTeam = new Incident("1", "REF-1", "Fire", "Fire", "Bajada", "high", mobilizing, "Rescue Team Alpha", "User", Instant.now(), List.of());
        assertNull(mobilizing.blocker(withTeam));

        IncidentStatus activeResponse = mobilizing.next().orElseThrow();
        assertEquals(IncidentStatus.ACTIVE_RESPONSE, activeResponse);

        IncidentStatus stabilized = activeResponse.next().orElseThrow();
        assertEquals(IncidentStatus.STABILIZED, stabilized);

        IncidentStatus closed = stabilized.next().orElseThrow();
        assertEquals(IncidentStatus.CLOSED, closed);
        assertTrue(closed.isTerminal());
        assertTrue(closed.next().isEmpty());
    }

    @Test
    void statusKeyParsingHandlesAliasesRobustly() {
        assertEquals(IncidentStatus.DRAFT, IncidentStatus.fromKey("submitted"));
        assertEquals(IncidentStatus.DRAFT, IncidentStatus.fromKey("pending_review"));
        assertEquals(IncidentStatus.PUBLISHED, IncidentStatus.fromKey("awaiting_response"));
        assertEquals(IncidentStatus.ACKNOWLEDGED, IncidentStatus.fromKey("responders_confirmed"));
        assertEquals(IncidentStatus.STABILIZED, IncidentStatus.fromKey("resolved"));
        assertEquals(IncidentStatus.CLOSED, IncidentStatus.fromKey("cancelled"));
        assertEquals(IncidentStatus.DRAFT, IncidentStatus.fromKey(null));
    }
}

