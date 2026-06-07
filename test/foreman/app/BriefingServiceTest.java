package foreman.app;

import foreman.domain.Role;
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

    private static Role role(String name, String sourceFile) {
        return new Role("id", name, "", sourceFile);
    }

    @Test
    void formatOpensWithYouAre() {
        var result = service.format("Foreman", "Dev Chat", null, null, List.of(), List.of(), Optional.empty());
        assertTrue(result.startsWith("You are the Dev Chat for Foreman."));
    }

    @Test
    void formatAlwaysIncludesClaludeMd() {
        var result = service.format("Foreman", "Dev", null, null, List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("- CLAUDE.md"));
    }

    @Test
    void formatIncludesRoleDocWithAnnotation() {
        var result = service.format("Foreman", "Dev", null, null, List.of(), List.of(),
                Optional.of(Path.of("docs/roles/dev.md")));
        assertTrue(result.contains("docs/roles/dev.md — role instructions"));
    }

    @Test
    void formatOmitsRoleDocLineWhenAbsent() {
        var result = service.format("Foreman", "Dev", null, null, List.of(), List.of(), Optional.empty());
        assertFalse(result.contains("role instructions"));
    }

    @Test
    void formatIncludesDesignDocs() {
        var docs = List.of(Path.of("docs/features/core-domain/design.md"));
        var result = service.format("Foreman", "Dev", null, null, List.of(), docs, Optional.empty());
        assertTrue(result.contains("docs/features/core-domain/design.md"));
    }

    @Test
    void formatSingleIssueSetsTask() {
        var issues = List.of(new BriefingService.IssueRef(11, "Bug: briefing identical"));
        var result = service.format("Foreman", "Dev", null, null, issues, List.of(), Optional.empty());
        assertTrue(result.contains("Your task: implement issue #11 — Bug: briefing identical."));
        assertTrue(result.contains("gh issue view 11"));
    }

    @Test
    void formatMultipleIssuesListsThem() {
        var issues = List.of(new BriefingService.IssueRef(4, "Project registration"),
                             new BriefingService.IssueRef(5, "Session panel"));
        var result = service.format("Foreman", "Dev", null, null, issues, List.of(), Optional.empty());
        assertTrue(result.contains("#4"));
        assertTrue(result.contains("Project registration"));
        assertTrue(result.contains("#5"));
        assertTrue(result.contains("Confirm with the user which to start with."));
    }

    @Test
    void formatNoIssuesShowsFallback() {
        var result = service.format("Foreman", "Dev", null, null, List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("no open issues found"));
    }

    @Test
    void formatEndsWithDoNotStart() {
        var result = service.format("Foreman", "Dev", null, null, List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("Do not start until you have read the files and issue(s) listed above."));
    }

    @Test
    void formatShowsPathsWhenWorkflowDiffersFromProject() {
        var result = service.format("Client", "Dev",
                "/client/repo", "/sidecars/client",
                List.of(), List.of(), Optional.empty());
        assertTrue(result.contains("Your working directory: /client/repo"));
        assertTrue(result.contains("Workflow docs:          /sidecars/client"));
    }

    @Test
    void formatOmitsPathsWhenWorkflowMatchesProject() {
        var result = service.format("Native", "Dev",
                "/my/project", "/my/project",
                List.of(), List.of(), Optional.empty());
        assertFalse(result.contains("Your working directory:"));
        assertFalse(result.contains("Workflow docs:"));
    }

    @Test
    void formatOmitsPathsWhenPathsAreNull() {
        var result = service.format("Native", "Dev",
                null, null,
                List.of(), List.of(), Optional.empty());
        assertFalse(result.contains("Your working directory:"));
        assertFalse(result.contains("Workflow docs:"));
    }

    // findRoleDoc — sourceFile takes priority

    @Test
    void findRoleDocUsesSourceFileDirectly(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev Chat");

        // name is "Dev Chat" which would never guess "dev.md" by name-conversion alone
        var result = service.findRoleDoc(root, role("Dev Chat", "dev.md"));
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev.md"), result.get());
    }

    @Test
    void findRoleDocFallsBackToNameGuessWhenSourceFileNull(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev");

        var result = service.findRoleDoc(root, role("Dev", null));
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev.md"), result.get());
    }

    @Test
    void findRoleDocFallsBackToNameGuessWhenSourceFileMissing(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        // sourceFile points to a non-existent file; fallback by name should find dev.md
        Files.writeString(rolesDir.resolve("dev.md"), "# Role: Dev");

        var result = service.findRoleDoc(root, role("Dev", "ghost.md"));
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev.md"), result.get());
    }

    @Test
    void findRoleDocLocatesHyphenatedFileByName(@TempDir Path root) throws IOException {
        var rolesDir = root.resolve("docs/roles");
        Files.createDirectories(rolesDir);
        Files.writeString(rolesDir.resolve("dev-chat.md"), "# Role: Dev Chat");

        var result = service.findRoleDoc(root, role("Dev Chat", null));
        assertTrue(result.isPresent());
        assertEquals(Path.of("docs/roles/dev-chat.md"), result.get());
    }

    @Test
    void findRoleDocReturnsEmptyWhenAbsent(@TempDir Path root) {
        assertTrue(service.findRoleDoc(root, role("Dev", null)).isEmpty());
    }

    // findDesignDocs

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
