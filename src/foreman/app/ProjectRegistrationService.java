package foreman.app;

import foreman.domain.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;

public class ProjectRegistrationService {

    private final RoleDiscoveryService discovery;

    public ProjectRegistrationService(RoleDiscoveryService discovery) {
        this.discovery = discovery;
    }

    public Project register(Path projectPath, String name, ForemanWorkspaceService workspaceService) {
        var workspace = workspaceService.getWorkspace();
        var roles     = new ArrayList<>(workspace.roles());
        var projects  = new ArrayList<>(workspace.projects());

        var discovered   = discovery.discover(projectPath);
        var assignments  = new ArrayList<RoleAssignment>();

        for (var candidate : discovered) {
            var existing = roles.stream()
                    .filter(r -> r.name().equals(candidate.name()))
                    .findFirst();
            var role = existing.orElseGet(() -> {
                roles.add(candidate);
                return candidate;
            });
            assignments.add(new RoleAssignment(role.id(), role.name()));
        }

        var project = new Project(
                UUID.randomUUID().toString(),
                name,
                projectPath.toAbsolutePath().toString(),
                "",
                new Team(assignments)
        );
        projects.add(project);

        workspaceService.setWorkspace(new ForemanWorkspace(roles, projects));
        workspaceService.save();
        return project;
    }
}
