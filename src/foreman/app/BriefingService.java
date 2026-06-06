package foreman.app;

import foreman.domain.Project;
import foreman.domain.Role;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class BriefingService {

    public record IssueRef(int number, String title) {}

    public String generate(Project project, Role role) {
        var issues     = fetchIssues(Path.of(project.path()));
        var designDocs = findDesignDocs(Path.of(project.path()));
        var roleDoc    = findRoleDoc(Path.of(project.path()), role.name());
        return format(project.name(), role.name(), issues, designDocs, roleDoc);
    }

    String format(String projectName, String roleName,
                  List<IssueRef> issues, List<Path> designDocs, Optional<Path> roleDoc) {
        var sb = new StringBuilder();
        sb.append("Hand off to ").append(roleName).append(" on ").append(projectName).append(":\n");

        roleDoc.ifPresent(p -> sb.append("\nRole instructions: ").append(p).append("\n"));

        sb.append("\nOpen issues:\n");
        if (issues.isEmpty()) {
            sb.append("  (could not fetch issues — run `gh issue list` manually)\n");
        } else {
            for (var issue : issues) {
                sb.append("  #").append(issue.number()).append(" — ").append(issue.title()).append("\n");
            }
        }

        if (!designDocs.isEmpty()) {
            sb.append("\nRelevant docs:\n");
            for (var doc : designDocs) {
                sb.append("  ").append(doc).append("\n");
            }
        }

        return sb.toString().stripTrailing();
    }

    Optional<Path> findRoleDoc(Path projectRoot, String roleName) {
        var rolesDir = projectRoot.resolve("docs/roles");
        if (!Files.isDirectory(rolesDir)) return Optional.empty();
        var candidates = List.of(
                roleName.toLowerCase().replace(' ', '-') + ".md",
                roleName.toLowerCase().replace(' ', '_') + ".md",
                roleName.toLowerCase() + ".md"
        );
        for (var candidate : candidates) {
            var path = rolesDir.resolve(candidate);
            if (Files.exists(path)) return Optional.of(projectRoot.relativize(path));
        }
        return Optional.empty();
    }

    List<Path> findDesignDocs(Path projectRoot) {
        var featuresDir = projectRoot.resolve("docs/features");
        if (!Files.isDirectory(featuresDir)) return Collections.emptyList();
        try (var walk = Files.walk(featuresDir)) {
            return walk
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .map(projectRoot::relativize)
                    .toList();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    List<IssueRef> fetchIssues(Path projectPath) {
        try {
            var process = new ProcessBuilder("gh", "issue", "list",
                    "--state", "open", "--json", "number,title")
                    .directory(projectPath.toFile())
                    .redirectErrorStream(true)
                    .start();
            var output = new String(process.getInputStream().readAllBytes());
            process.waitFor();
            return parseIssueJson(output);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private List<IssueRef> parseIssueJson(String json) {
        json = json.strip();
        if (!json.startsWith("[")) return Collections.emptyList();
        var issues = new ArrayList<IssueRef>();
        // minimal hand-rolled parse — avoids adding a JSON dep just for this
        var entries = json.substring(1, json.lastIndexOf(']')).split("\\},\\s*\\{");
        for (var entry : entries) {
            try {
                var num   = Integer.parseInt(extract(entry, "\"number\""));
                var title = extract(entry, "\"title\"");
                issues.add(new IssueRef(num, title));
            } catch (NumberFormatException ignored) {}
        }
        issues.sort((a, b) -> Integer.compare(a.number(), b.number()));
        return issues;
    }

    private String extract(String json, String key) {
        var idx = json.indexOf(key);
        if (idx < 0) return "";
        var rest = json.substring(idx + key.length()).stripLeading();
        if (!rest.startsWith(":")) return "";
        rest = rest.substring(1).stripLeading();
        if (rest.startsWith("\"")) {
            var end = rest.indexOf('"', 1);
            return end < 0 ? "" : rest.substring(1, end);
        }
        // numeric
        var end = rest.indexOf(',');
        if (end < 0) end = rest.indexOf('}');
        return end < 0 ? rest.strip() : rest.substring(0, end).strip();
    }
}
