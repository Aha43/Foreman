package foreman.domain;

import java.util.List;

public record ForemanWorkspace(List<Role> roles, List<Project> projects) {

    public static ForemanWorkspace empty() {
        return new ForemanWorkspace(List.of(), List.of());
    }

    public ForemanWorkspace withoutProject(String projectId) {
        var remaining = projects.stream().filter(p -> !p.id().equals(projectId)).toList();
        return new ForemanWorkspace(roles, remaining);
    }
}
