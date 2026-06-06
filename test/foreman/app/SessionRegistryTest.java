package foreman.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    @Test
    void toggleAbsentKeyCreatesActiveSession() {
        registry.toggle("proj-1", "role-1");
        var sessions = registry.getSessions();
        assertEquals(1, sessions.size());
        assertTrue(sessions.get(0).active());
        assertEquals("proj-1", sessions.get(0).projectId());
        assertEquals("role-1", sessions.get(0).roleId());
    }

    @Test
    void toggleActiveBecomesIdle() {
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-1");
        assertFalse(registry.getSessions().get(0).active());
    }

    @Test
    void toggleIdleBecomesActive() {
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-1");
        assertTrue(registry.getSessions().get(0).active());
    }

    @Test
    void getActiveSessionsFiltersInactive() {
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-2");
        registry.toggle("proj-1", "role-2"); // now idle
        assertEquals(1, registry.getActiveSessions().size());
        assertEquals("role-1", registry.getActiveSessions().get(0).roleId());
    }

    @Test
    void toggleNotifiesListeners() {
        var called = new int[]{0};
        registry.onChange(() -> called[0]++);
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-1");
        assertEquals(2, called[0]);
    }

    @Test
    void idIsStableAcrossToggles() {
        registry.toggle("proj-1", "role-1");
        var id1 = registry.getSessions().get(0).id();
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-1");
        var id2 = registry.getSessions().get(0).id();
        assertEquals(id1, id2);
    }
}
