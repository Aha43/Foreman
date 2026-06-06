package foreman.app;

import foreman.domain.Role;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RoleDiscoveryService {

    public List<Role> discover(Path projectRoot) {
        var rolesDir = projectRoot.resolve("docs/roles");
        if (!Files.isDirectory(rolesDir)) {
            return Collections.emptyList();
        }
        try (var stream = Files.list(rolesDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .map(this::toRole)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan roles dir: " + rolesDir, e);
        }
    }

    private Role toRole(Path file) {
        try {
            var content = Files.readString(file);
            var name = extractName(content, file.getFileName().toString());
            return new Role(UUID.randomUUID().toString(), name, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read role file: " + file, e);
        }
    }

    String extractName(String content, String filename) {
        var firstLine = content.lines().findFirst().orElse("").strip();
        if (firstLine.startsWith("# Role: ")) {
            return firstLine.substring("# Role: ".length()).strip();
        }
        if (firstLine.startsWith("# ")) {
            return firstLine.substring(2).strip();
        }
        return titleCase(filename.replaceAll("\\.md$", "").replace('-', ' ').replace('_', ' '));
    }

    private String titleCase(String s) {
        if (s.isBlank()) return s;
        var words = s.split(" ");
        var sb = new StringBuilder();
        for (var word : words) {
            if (word.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0)));
            sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
