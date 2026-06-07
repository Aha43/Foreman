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

    public Project register(Path projectPath, String name, String workflowPath,
                           ForemanWorkspaceService workspaceService) {
        var workspace = workspaceService.getWorkspace();
        var roles     = new ArrayList<>(workspace.roles());
        var projects  = new ArrayList<>(workspace.projects());

        var discoveryRoot = (workflowPath != null && !workflowPath.isBlank())
                ? Path.of(workflowPath) : projectPath;
        var discovered   = discovery.discover(discoveryRoot);
        var assignments  = new ArrayList<RoleAssignment>();

        for (var candidate : discovered) {
            var existing = roles.stream()
                    .filter(r -> r.name().equals(candidate.name()))
                    .findFirst();
            var role = existing.orElseGet(() -> {
                roles.add(candidate);
                return candidate;
            });
            assignments.add(new RoleAssignment(role.id(), displayLabel(role.name())));
        }

        var normalizedWorkflowPath = (workflowPath != null && !workflowPath.isBlank()) ? workflowPath : null;
        var project = new Project(
                UUID.randomUUID().toString(),
                name,
                projectPath.toAbsolutePath().toString(),
                "",
                new Team(assignments),
                normalizedWorkflowPath
        );
        projects.add(project);

        workspaceService.setWorkspace(new ForemanWorkspace(roles, projects));
        workspaceService.save();
        return project;
    }

    public Project rescan(Project project, ForemanWorkspaceService workspaceService) {
        var workspace    = workspaceService.getWorkspace();
        var roles        = new ArrayList<>(workspace.roles());
        var projects     = new ArrayList<>(workspace.projects());
        var workflowRoot = Path.of(project.effectiveWorkflowPath());

        var discovered  = discovery.discover(workflowRoot);
        var assignments = new ArrayList<>(project.team().assignments());

        for (var candidate : discovered) {
            var existing = roles.stream()
                    .filter(r -> r.name().equals(candidate.name()))
                    .findFirst();
            var role = existing.orElseGet(() -> {
                roles.add(candidate);
                return candidate;
            });
            var alreadyAssigned = assignments.stream()
                    .anyMatch(a -> a.roleId().equals(role.id()));
            if (!alreadyAssigned) {
                assignments.add(new RoleAssignment(role.id(), displayLabel(role.name())));
            }
        }

        var updated = new Project(project.id(), project.name(), project.path(),
                project.description(), new Team(assignments), project.workflowPath());
        var updatedProjects = projects.stream()
                .map(p -> p.id().equals(project.id()) ? updated : p)
                .toList();

        workspaceService.setWorkspace(new ForemanWorkspace(roles, updatedProjects));
        workspaceService.save();
        return updated;
    }

    static String displayLabel(String roleName) {
        if (roleName.toLowerCase().endsWith(" chat")) {
            return roleName.substring(0, roleName.length() - 5).strip();
        }
        return roleName;
    }
}
