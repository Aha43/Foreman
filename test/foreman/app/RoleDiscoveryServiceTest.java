package foreman.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RoleDiscoveryServiceTest {

    private final RoleDiscoveryService service = new RoleDiscoveryService();

    @Test
    void extractsNameFromRoleHeading() {
        assertEquals("Dev", service.extractName("# Role: Dev\nrest", "dev.md"));
    }

    @Test
    void extractsNameFromPlainHeading() {
        assertEquals("Planner", service.extractName("# Planner\nrest", "planning.md"));
    }

    @Test
    void fallsBackToFilename() {
        assertEquals("My Planner", service.extractName("no heading here", "my-planner.md"));
    }

    @Test
    void filenameWithUnderscoreTitleCased() {
        assertEquals("Dog Fooder", service.extractName("", "dog_fooder.md"));
    }

    @Test
    void returnsEmptyListWhenRolesDirAbsent(@TempDir Path root) {
        assertTrue(service.discover(root).isEmpty());
    }

    @Test
    void discoversRolesFromFiles(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev\nYou write code.");
        Files.writeString(rolesDir.resolve("planning.md"), "# Planner\nYou plan.");

        var roles = service.discover(root);
        assertEquals(2, roles.size());

        var names = roles.stream().map(r -> r.name()).toList();
        assertTrue(names.contains("Dev"));
        assertTrue(names.contains("Planner"));
    }

    @Test
    void instructionsAreFullFileContent(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        var content = "# Role: Dev\nYou write code.\nBe careful.";
        Files.writeString(rolesDir.resolve("dev.md"), content);

        var roles = service.discover(root);
        assertEquals(content, roles.get(0).instructions());
    }

    @Test
    void ignoresNonMdFiles(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev");
        Files.writeString(rolesDir.resolve("notes.txt"), "ignored");

        assertEquals(1, service.discover(root).size());
    }
}
