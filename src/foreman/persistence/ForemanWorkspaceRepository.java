package foreman.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import foreman.domain.ForemanWorkspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForemanWorkspaceRepository {

    private final ObjectMapper mapper;

    public ForemanWorkspaceRepository() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ForemanWorkspace load(Path path) {
        if (!Files.exists(path)) {
            return ForemanWorkspace.empty();
        }
        try {
            return mapper.readValue(path.toFile(), ForemanWorkspace.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load workspace: " + path, e);
        }
    }

    public void save(ForemanWorkspace workspace, Path path) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), workspace);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save workspace: " + path, e);
        }
    }
}
