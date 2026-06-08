package foreman.app;

import foreman.domain.Session;

import java.util.*;

public class SessionRegistry {

    private final Map<String, List<Session>> sessions = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public Session launchSession(String projectId, String roleId) {
        var key = projectId + ":" + roleId;
        var list = sessions.computeIfAbsent(key, k -> new ArrayList<>());
        int index = list.size() + 1;
        var session = new Session(UUID.randomUUID().toString(), projectId, roleId, true, index);
        list.add(session);
        listeners.forEach(Runnable::run);
        return session;
    }

    public void stopSession(String sessionId) {
        for (var list : sessions.values()) {
            if (list.removeIf(s -> s.id().equals(sessionId))) {
                listeners.forEach(Runnable::run);
                return;
            }
        }
    }

    public List<Session> getSessionsForRole(String projectId, String roleId) {
        return List.copyOf(sessions.getOrDefault(projectId + ":" + roleId, List.of()));
    }

    // Backward compat: toggle the first session for a role, or create one (index=1) if none exist.
    // Used by the non-macOS manual-toggle path in SessionPanel.
    public void toggle(String projectId, String roleId) {
        var key = projectId + ":" + roleId;
        var list = sessions.computeIfAbsent(key, k -> new ArrayList<>());
        if (list.isEmpty()) {
            list.add(new Session(UUID.randomUUID().toString(), projectId, roleId, true, 1));
        } else {
            var s = list.get(0);
            list.set(0, new Session(s.id(), s.projectId(), s.roleId(), !s.active(), s.index()));
        }
        listeners.forEach(Runnable::run);
    }

    // Backward compat: create or update the first session for a role.
    // Used by startup restore and single-session launch in SessionPanel.
    public void setRunning(String projectId, String roleId, boolean running) {
        var key = projectId + ":" + roleId;
        var list = sessions.computeIfAbsent(key, k -> new ArrayList<>());
        if (list.isEmpty()) {
            list.add(new Session(UUID.randomUUID().toString(), projectId, roleId, running, 1));
        } else {
            var s = list.get(0);
            list.set(0, new Session(s.id(), s.projectId(), s.roleId(), running, s.index()));
        }
        listeners.forEach(Runnable::run);
    }

    public List<Session> getSessions() {
        return sessions.values().stream().flatMap(List::stream).toList();
    }

    public List<Session> getActiveSessions() {
        return sessions.values().stream().flatMap(List::stream)
                .filter(Session::active).toList();
    }

    public void dropProject(String projectId) {
        sessions.keySet().removeIf(k -> k.startsWith(projectId + ":"));
    }

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }
}
