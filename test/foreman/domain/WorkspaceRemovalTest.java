package foreman.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceRemovalTest {

    private static Project project(String id, String name) {
        return new Project(id, name, "/path", "", new Team(List.of()), null);
    }

    @Test
    void withoutProjectRemovesMatchingProject() {
        var ws = new ForemanWorkspace(
                List.of(),
                List.of(project("a", "Alpha"), project("b", "Beta")));
        var result = ws.withoutProject("a");
        assertEquals(1, result.projects().size());
        assertEquals("b", result.projects().get(0).id());
    }

    @Test
    void withoutProjectLeavesRolesUntouched() {
        var role = new Role("r1", "Dev", "instructions", "dev.md");
        var ws = new ForemanWorkspace(List.of(role), List.of(project("a", "Alpha")));
        var result = ws.withoutProject("a");
        assertEquals(1, result.roles().size());
        assertTrue(result.projects().isEmpty());
    }

    @Test
    void withoutProjectIsNoOpForUnknownId() {
        var ws = new ForemanWorkspace(List.of(), List.of(project("a", "Alpha")));
        var result = ws.withoutProject("nonexistent");
        assertEquals(1, result.projects().size());
    }
}
