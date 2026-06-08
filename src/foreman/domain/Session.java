package foreman.domain;

public record Session(String id, String projectId, String roleId, boolean active, int index) {}
