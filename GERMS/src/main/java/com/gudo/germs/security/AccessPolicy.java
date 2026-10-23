package com.gudo.germs.security;

import com.gudo.germs.models.Role;

import java.util.Arrays;
import java.util.List;

/** UI-level navigation filter. NOT a security boundary: the database enforces the real rules through RLS and RPCs. */
public final class AccessPolicy {
    private AccessPolicy() { }

    public static List<Module> modulesFor(Role role) {
        return Arrays.stream(Module.values()).filter(m -> m.allows(role)).toList();
    }
}
