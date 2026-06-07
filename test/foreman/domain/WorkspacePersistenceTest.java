package foreman.domain;

import foreman.persistence.ForemanWorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspacePersistenceTest {

    private final ForemanWorkspaceRepository repo = new ForemanWorkspaceRepository();

    @Test
    void loadReturnsEmptyWorkspaceWhenFileAbsent(@TempDir Path dir) {
        var result = repo.load(dir.resolve("nonexistent.json"));
        assertTrue(result.roles().isEmpty());
        assertTrue(result.projects().isEmpty());
    }

    @Test
    void roundTripPreservesWorkspace(@TempDir Path dir) {
        var role = new Role("role-1", "Dev", "You are the dev.", "dev.md");
        var assignment = new RoleAssignment("role-1", "Dev");
        var team = new Team(List.of(assignment));
        var project = new Project("proj-1", "Foreman", "/home/user/foreman", "The app itself", team, null);
        var workspace = new ForemanWorkspace(List.of(role), List.of(project));

        var file = dir.resolve("workspace.json");
        repo.save(workspace, file);
        var loaded = repo.load(file);

        assertEquals(1, loaded.roles().size());
        var loadedRole = loaded.roles().get(0);
        assertEquals("role-1", loadedRole.id());
        assertEquals("Dev", loadedRole.name());
        assertEquals("You are the dev.", loadedRole.instructions());

        assertEquals(1, loaded.projects().size());
        var loadedProject = loaded.projects().get(0);
        assertEquals("proj-1", loadedProject.id());
        assertEquals("Foreman", loadedProject.name());
        assertEquals("/home/user/foreman", loadedProject.path());
        assertEquals("The app itself", loadedProject.description());

        var loadedAssignments = loadedProject.team().assignments();
        assertEquals(1, loadedAssignments.size());
        assertEquals("role-1", loadedAssignments.get(0).roleId());
        assertEquals("Dev", loadedAssignments.get(0).label());
    }

    @Test
    void workflowPathIsNullWhenAbsentInJson(@TempDir Path dir) throws Exception {
        var file = dir.resolve("workspace.json");
        java.nio.file.Files.writeString(file,
                "{\"roles\":[],\"projects\":[{\"id\":\"p1\",\"name\":\"Old\",\"path\":\"/old\"," +
                "\"description\":\"\",\"team\":{\"assignments\":[]}}]}");
        var loaded = repo.load(file);
        assertNull(loaded.projects().get(0).workflowPath());
    }

    @Test
    void workflowPathRoundTrips(@TempDir Path dir) {
        var role       = new Role("r1", "Dev", "", "dev.md");
        var team       = new Team(List.of());
        var project    = new Project("p1", "Sidecar", "/project", "", team, "/sidecar");
        var workspace  = new ForemanWorkspace(List.of(role), List.of(project));
        var file       = dir.resolve("workspace.json");
        repo.save(workspace, file);
        var loaded = repo.load(file);
        assertEquals("/sidecar", loaded.projects().get(0).workflowPath());
    }

    @Test
    void saveCreatesMissingParentDirectories(@TempDir Path dir) {
        var workspace = ForemanWorkspace.empty();
        var file = dir.resolve("a/b/c/workspace.json");
        repo.save(workspace, file);
        assertTrue(file.toFile().exists());
    }
}
