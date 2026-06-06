package foreman.domain;

public record Project(String id, String name, String path, String description, Team team) {}
