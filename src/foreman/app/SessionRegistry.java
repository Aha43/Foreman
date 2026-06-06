package foreman.app;

import foreman.domain.Session;

import java.util.*;

public class SessionRegistry {

    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void toggle(String projectId, String roleId) {
        var key = projectId + ":" + roleId;
        sessions.compute(key, (k, existing) -> existing == null
                ? new Session(UUID.randomUUID().toString(), projectId, roleId, true)
                : new Session(existing.id(), projectId, roleId, !existing.active()));
        listeners.forEach(Runnable::run);
    }

    public List<Session> getSessions() {
        return List.copyOf(sessions.values());
    }

    public List<Session> getActiveSessions() {
        return sessions.values().stream().filter(Session::active).toList();
    }

    public void onChange(Runnable listener) {
        listeners.add(listener);
    }
}
