package foreman.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForemanSettingsRepository {

    private final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ForemanSettings load(Path path) {
        if (!Files.exists(path)) return ForemanSettings.defaults();
        try {
            return mapper.readValue(path.toFile(), ForemanSettings.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load settings: " + path, e);
        }
    }

    public void save(ForemanSettings settings, Path path) {
        try {
            Files.createDirectories(path.getParent());
            mapper.writeValue(path.toFile(), settings);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save settings: " + path, e);
        }
    }
}
