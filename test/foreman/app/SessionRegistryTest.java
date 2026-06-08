package foreman.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    // ── toggle (non-macOS backward-compat path) ───────────────────────────────

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

    // ── setRunning (startup restore backward-compat path) ────────────────────

    @Test
    void setRunningCreatesRunningSessionWhenAbsent() {
        registry.setRunning("proj-1", "role-1", true);
        var sessions = registry.getSessions();
        assertEquals(1, sessions.size());
        assertTrue(sessions.get(0).active());
    }

    @Test
    void setRunningUpdatesExistingSession() {
        registry.toggle("proj-1", "role-1"); // active=true
        registry.setRunning("proj-1", "role-1", false);
        assertFalse(registry.getSessions().get(0).active());
    }

    @Test
    void setRunningPreservesSessionId() {
        registry.toggle("proj-1", "role-1");
        var id1 = registry.getSessions().get(0).id();
        registry.setRunning("proj-1", "role-1", false);
        registry.setRunning("proj-1", "role-1", true);
        assertEquals(id1, registry.getSessions().get(0).id());
    }

    @Test
    void setRunningNotifiesListeners() {
        var called = new int[]{0};
        registry.onChange(() -> called[0]++);
        registry.setRunning("proj-1", "role-1", true);
        assertEquals(1, called[0]);
    }

    // ── getActiveSessions / dropProject ──────────────────────────────────────

    @Test
    void getActiveSessionsFiltersInactive() {
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-2");
        registry.toggle("proj-1", "role-2"); // now idle
        assertEquals(1, registry.getActiveSessions().size());
        assertEquals("role-1", registry.getActiveSessions().get(0).roleId());
    }

    @Test
    void dropProjectRemovesAllSessionsForThatProject() {
        registry.toggle("proj-1", "role-1");
        registry.toggle("proj-1", "role-2");
        registry.toggle("proj-2", "role-1");
        registry.dropProject("proj-1");
        var remaining = registry.getSessions();
        assertEquals(1, remaining.size());
        assertEquals("proj-2", remaining.get(0).projectId());
    }

    // ── launchSession ─────────────────────────────────────────────────────────

    @Test
    void launchSessionCreatesSessionWithIndex1() {
        var s = registry.launchSession("proj-1", "role-1");
        assertEquals(1, s.index());
        assertTrue(s.active());
        assertEquals("proj-1", s.projectId());
        assertEquals("role-1", s.roleId());
    }

    @Test
    void launchSessionSecondSessionGetsIndex2() {
        registry.launchSession("proj-1", "role-1");
        var s2 = registry.launchSession("proj-1", "role-1");
        assertEquals(2, s2.index());
    }

    @Test
    void launchSessionThirdSessionGetsIndex3() {
        registry.launchSession("proj-1", "role-1");
        registry.launchSession("proj-1", "role-1");
        var s3 = registry.launchSession("proj-1", "role-1");
        assertEquals(3, s3.index());
    }

    @Test
    void launchSessionAppendsToFlatSessionsList() {
        registry.launchSession("proj-1", "role-1");
        registry.launchSession("proj-1", "role-1");
        assertEquals(2, registry.getSessions().size());
    }

    @Test
    void launchSessionNotifiesListeners() {
        var called = new int[]{0};
        registry.onChange(() -> called[0]++);
        registry.launchSession("proj-1", "role-1");
        assertEquals(1, called[0]);
    }

    @Test
    void launchSessionIdsAreDistinct() {
        var s1 = registry.launchSession("proj-1", "role-1");
        var s2 = registry.launchSession("proj-1", "role-1");
        assertNotEquals(s1.id(), s2.id());
    }

    // ── stopSession ───────────────────────────────────────────────────────────

    @Test
    void stopSessionRemovesById() {
        var s1 = registry.launchSession("proj-1", "role-1");
        registry.launchSession("proj-1", "role-1");
        registry.stopSession(s1.id());
        assertEquals(1, registry.getSessions().size());
    }

    @Test
    void stopSessionLeavesGapInIndex() {
        registry.launchSession("proj-1", "role-1");
        var s2 = registry.launchSession("proj-1", "role-1");
        var s3 = registry.launchSession("proj-1", "role-1");
        registry.stopSession(s2.id());
        // s1 and s3 remain; s3 keeps index=3
        var remaining = registry.getSessions();
        assertEquals(2, remaining.size());
        assertEquals(3, remaining.stream().filter(s -> s.id().equals(s3.id()))
                .findFirst().orElseThrow().index());
    }

    @Test
    void stopSessionNotifiesListeners() {
        var s = registry.launchSession("proj-1", "role-1");
        var called = new int[]{0};
        registry.onChange(() -> called[0]++);
        registry.stopSession(s.id());
        assertEquals(1, called[0]);
    }

    @Test
    void stopSessionUnknownIdDoesNotThrowOrNotify() {
        var called = new int[]{0};
        registry.onChange(() -> called[0]++);
        assertDoesNotThrow(() -> registry.stopSession("no-such-id"));
        assertEquals(0, called[0]);
    }

    // ── getSessionsForRole ────────────────────────────────────────────────────

    @Test
    void getSessionsForRoleReturnsEmptyWhenNoneExist() {
        assertTrue(registry.getSessionsForRole("proj-1", "role-1").isEmpty());
    }

    @Test
    void getSessionsForRoleReturnsAllSessionsForThatRole() {
        registry.launchSession("proj-1", "role-1");
        registry.launchSession("proj-1", "role-1");
        registry.launchSession("proj-1", "role-2"); // different role
        assertEquals(2, registry.getSessionsForRole("proj-1", "role-1").size());
    }

    @Test
    void getSessionsForRoleDoesNotIncludeOtherRoles() {
        registry.launchSession("proj-1", "role-2");
        assertTrue(registry.getSessionsForRole("proj-1", "role-1").isEmpty());
    }
}
