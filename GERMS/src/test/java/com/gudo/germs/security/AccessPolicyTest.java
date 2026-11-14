package com.gudo.germs.security;

import com.gudo.germs.config.AppConfig;
import com.gudo.germs.models.Role;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    @Test void adminSeesAllFourteenCoreModules() {
        long count = AccessPolicy.modulesFor(Role.ADMIN).stream().filter(m -> !m.name().startsWith("GUEST_") && !m.name().startsWith("MY_")).count();
        assertEquals(14, count);
    }

    @Test void responderCannotSeeAdministrativeModules() {
        var modules = AccessPolicy.modulesFor(Role.RESPONDER);
        assertFalse(modules.contains(Module.USERS));
        assertFalse(modules.contains(Module.AUDIT));
        assertFalse(modules.contains(Module.DISPATCH));
        assertFalse(modules.contains(Module.TEAMS));
        assertTrue(modules.contains(Module.INCIDENTS));
        assertTrue(modules.contains(Module.MY_DEPLOYMENTS));
    }

    @Test void guestSeesOnlyPublicModules() {
        var modules = AccessPolicy.modulesFor(Role.GUEST);
        assertTrue(modules.contains(Module.GUEST_REPORT));
        assertFalse(modules.contains(Module.INCIDENTS));
        assertFalse(modules.contains(Module.DASHBOARD));
    }

    @Test void privilegedKeysAreRejected() {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"service_role\"}".getBytes());
        assertTrue(AppConfig.isPrivilegedKey("aaa." + payload + ".sig"));
        assertTrue(AppConfig.isPrivilegedKey("sb_secret_abc"));
        String anon = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"anon\"}".getBytes());
        assertFalse(AppConfig.isPrivilegedKey("aaa." + anon + ".sig"));
        assertFalse(AppConfig.isPrivilegedKey("sb_publishable_abc"));
    }
}
