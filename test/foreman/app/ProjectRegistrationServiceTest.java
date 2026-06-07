package foreman.app;

import foreman.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRegistrationServiceTest {

    private final ProjectRegistrationService service =
            new ProjectRegistrationService(new RoleDiscoveryService());

    @Test
    void stripsChatSuffix() {
        assertEquals("Dev", ProjectRegistrationService.displayLabel("Dev Chat"));
    }

    @Test
    void stripsTrailingChatCaseInsensitive() {
        assertEquals("Dev", ProjectRegistrationService.displayLabel("Dev CHAT"));
    }

    @Test
    void preservesNameWithoutChatSuffix() {
        assertEquals("Planner", ProjectRegistrationService.displayLabel("Planner"));
    }

    @Test
    void doesNotStripChatInMiddleOfName() {
        assertEquals("ChatBot", ProjectRegistrationService.displayLabel("ChatBot"));
    }

    // rescan tests

    @Test
    void rescanAddsNewRoleAndAssignment(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Dev");

        var workspaceService = workspaceWith(root, List.of());
        var project = service.register(root, "Proj", null, workspaceService);
        assertEquals(1, project.team().assignments().size());

        // add a second role file
        Files.writeString(rolesDir.resolve("planner.md"), "# Planner");
        var updated = service.rescan(project, workspaceService);

        assertEquals(2, updated.team().assignments().size());
        assertTrue(updated.team().assignments().stream().anyMatch(a -> a.label().equals("Planner")));
    }

    @Test
    void rescanDoesNotDuplicateExistingAssignments(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Dev");

        var workspaceService = workspaceWith(root, List.of());
        var project = service.register(root, "Proj", null, workspaceService);
        assertEquals(1, project.team().assignments().size());

        // rescan with no new files
        var updated = service.rescan(project, workspaceService);
        assertEquals(1, updated.team().assignments().size());
    }

    @Test
    void rescanLeavesExistingAssignmentsWhenNoFilesFound(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Dev");

        var workspaceService = workspaceWith(root, List.of());
        var project = service.register(root, "Proj", null, workspaceService);

        // delete the role file, then rescan — existing assignment must be preserved
        Files.delete(rolesDir.resolve("dev.md"));
        var updated = service.rescan(project, workspaceService);
        assertEquals(1, updated.team().assignments().size());
        assertEquals("Dev", updated.team().assignments().get(0).label());
    }

    private ForemanWorkspaceService workspaceWith(Path root, List<Role> roles) {
        var workspace = new ForemanWorkspace(new java.util.ArrayList<>(roles),
                new java.util.ArrayList<>());
        return new ForemanWorkspaceService(false) {
            private ForemanWorkspace ws = workspace;
            @Override public ForemanWorkspace getWorkspace() { return ws; }
            @Override public void setWorkspace(ForemanWorkspace w) { ws = w; }
            @Override public void save() {}
        };
    }
}
