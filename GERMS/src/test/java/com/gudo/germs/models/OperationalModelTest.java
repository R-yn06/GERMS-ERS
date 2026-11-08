package com.gudo.germs.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationalModelTest {

    @Test
    void roleParsingMapsGuestAndFallbacks() {
        assertEquals(Role.ADMIN, Role.fromDb("admin"));
        assertEquals(Role.ADMIN, Role.fromDb("ADMIN"));
        assertEquals(Role.RESPONDER, Role.fromDb("responder"));
        assertEquals(Role.GUEST, Role.fromDb("guest"));
        assertEquals(Role.GUEST, Role.fromDb("user"));
        assertEquals(Role.GUEST, Role.fromDb("unknown"));
        assertEquals(Role.GUEST, Role.fromDb(null));
    }

    @Test
    void profileParsingParsesValidJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode n = mapper.createObjectNode();
        UUID uid = UUID.randomUUID();
        n.put("id", uid.toString());
        n.put("full_name", "Jane Doe");
        n.put("email", "jane@redcross.org.ph");
        n.put("role", "responder");
        n.put("is_active", true);

        Profile p = Profile.fromJson(n);
        assertNotNull(p);
        assertEquals(uid, p.id());
        assertEquals("Jane Doe", p.fullName());
        assertEquals(Role.RESPONDER, p.role());
        assertTrue(p.active());
    }

    @Test
    void guestProfileFactoryCreatesValidDefaults() {
        Profile guest = Profile.guest();
        assertNotNull(guest);
        assertEquals(Role.GUEST, guest.role());
        assertEquals("Guest", guest.fullName());
        assertTrue(guest.active());
    }

    @Test
    void responseTeamModelSerializesProperly() {
        UUID tid = UUID.randomUUID();
        ResponseTeam team = new ResponseTeam(tid, "Rescue Team Alpha");
        assertEquals("Rescue Team Alpha", team.toString());
        assertEquals("Rescue Team Alpha", team.name());
        assertEquals(tid, team.id());
    }
}

