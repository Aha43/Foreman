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

    private static final String GH = resolveGh();

    private static String resolveGh() {
        try {
            var proc = new ProcessBuilder("which", "gh").redirectErrorStream(true).start();
            var path = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            return path.isBlank() ? "gh" : path;
        } catch (Exception e) {
            return "gh";
        }
    }

    public String generate(Project project, Role role) {
        var projectPath  = Path.of(project.path());
        var workflowPath = Path.of(project.effectiveWorkflowPath());
        var issues     = fetchIssues(projectPath);
        var designDocs = findDesignDocs(workflowPath);
        var roleDoc    = findRoleDoc(workflowPath, role);
        return format(project.name(), role.name(), project.path(), project.effectiveWorkflowPath(),
                      issues, designDocs, roleDoc);
    }

    String format(String projectName, String roleName,
                  String projectPath, String workflowPath,
                  List<IssueRef> issues, List<Path> designDocs, Optional<Path> roleDoc) {
        var sb = new StringBuilder();
        sb.append("You are the ").append(roleName).append(" for ").append(projectName).append(".\n");

        var isSidecar = projectPath != null && workflowPath != null && !workflowPath.equals(projectPath);
        if (isSidecar) {
            sb.append("\nYour working directory: ").append(projectPath).append("\n");
            sb.append("Workflow docs:          ").append(workflowPath).append("\n");
        }

        sb.append("\nBefore starting, read:\n");
        sb.append("  - CLAUDE.md\n");
        roleDoc.ifPresent(p -> sb.append("  - ").append(p).append(" — role instructions\n"));
        for (var doc : designDocs) {
            sb.append("  - ").append(doc).append("\n");
        }

        sb.append("\n");
        if (issues.isEmpty()) {
            sb.append("(no open issues found — run `gh issue list` manually)\n");
        } else if (issues.size() == 1) {
            var issue = issues.get(0);
            sb.append("Your task: implement issue #").append(issue.number())
              .append(" — ").append(issue.title()).append(".\n");
            sb.append("Run `gh issue view ").append(issue.number()).append("` to read it in full.\n");
        } else {
            sb.append("Open issues to review:\n");
            for (int i = 0; i < issues.size(); i++) {
                var issue = issues.get(i);
                sb.append("  ").append(i + 1).append(". #").append(issue.number())
                  .append(" — ").append(issue.title()).append("\n");
            }
            sb.append("Confirm with the user which to start with.\n");
        }

        sb.append("\nDo not start until you have read the files and issue(s) listed above.");
        return sb.toString();
    }

    Optional<Path> findRoleDoc(Path projectRoot, Role role) {
        var rolesDir = projectRoot.resolve("docs/roles");
        if (!Files.isDirectory(rolesDir)) return Optional.empty();
        // use the stored source filename when available (set at discovery time)
        if (role.sourceFile() != null && !role.sourceFile().isBlank()) {
            var path = rolesDir.resolve(role.sourceFile());
            if (Files.exists(path)) return Optional.of(projectRoot.relativize(path));
        }
        // fallback: guess from role name (covers manually created roles)
        var roleName = role.name();
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
            var process = new ProcessBuilder(GH, "issue", "list",
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
