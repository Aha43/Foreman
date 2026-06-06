package foreman.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BriefingServiceTest {

    private final BriefingService service = new BriefingService();

    @Test
    void formatIncludesRoleAndProject() {
        var result = service.format("Foreman", "Dev", List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("Dev"));
        assertTrue(result.contains("Foreman"));
    }

    @Test
    void formatListsIssues() {
        var issues = List.of(new BriefingService.IssueRef(4, "Project registration"),
                             new BriefingService.IssueRef(5, "Session panel"));
        var result = service.format("Foreman", "Dev", issues, List.of(), Optional.empty());
        assertTrue(result.contains("#4"));
        assertTrue(result.contains("Project registration"));
        assertTrue(result.contains("#5"));
    }

    @Test
    void formatShowsFallbackWhenNoIssues() {
        var result = service.format("Foreman", "Dev", List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("could not fetch issues"));
    }

    @Test
    void formatIncludesDesignDocs() {
        var docs = List.of(Path.of("docs/features/core-domain/design.md"));
        var result = service.format("Foreman", "Dev", List.of(), docs, Optional.empty());
        assertTrue(result.contains("docs/features/core-domain/design.md"));
    }

    @Test
    void formatIncludesRoleDocWhenPresent() {
        var result = service.format("Foreman", "Dev", List.of(), List.of(),
                Optional.of(Path.of("docs/roles/dev.md")));
        assertTrue(result.contains("docs/roles/dev.md"));
    }

    @Test
    void formatOmitsRoleDocSectionWhenAbsent() {
        var result = service.format("Foreman", "Dev", List.of(), List.of(), Optional.empty());
        assertFalse(result.contains("Role instructions:"));
    }

    @Test
    void findRoleDocLocatesFileByLowercaseName(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev");

        var result = service.findRoleDoc(root, "Dev");
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev.md"), result.get());
    }

    @Test
    void findRoleDocLocatesHyphenatedFile(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev-chat.md"), "# Role: Dev Chat");

        var result = service.findRoleDoc(root, "Dev Chat");
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev-chat.md"), result.get());
    }

    @Test
    void findRoleDocReturnsEmptyWhenAbsent(@TempDir Path root) {
        assertTrue(service.findRoleDoc(root, "Dev").isEmpty());
    }

    @Test
    void findDesignDocsReturnsRelativePaths(@TempDir Path root) throws IOException {
        var featuresDir = root.resolve("docs/features/core-domain");
        Files.createDirectories(featuresDir);
        Files.writeString(featuresDir.resolve("design.md"), "# Design");

        var docs = service.findDesignDocs(root);
        assertEquals(1, docs.size());
        assertEquals(Path.of("docs/features/core-domain/design.md"), docs.get(0));
    }

    @Test
    void findDesignDocsReturnsEmptyWhenDirAbsent(@TempDir Path root) {
        assertTrue(service.findDesignDocs(root).isEmpty());
    }
}
