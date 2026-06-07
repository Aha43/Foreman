package foreman.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ForemanSettingsRepositoryTest {

    private final ForemanSettingsRepository repo = new ForemanSettingsRepository();

    @Test
    void loadReturnsDefaultsWhenFileAbsent(@TempDir Path dir) {
        var settings = repo.load(dir.resolve("settings.json"));
        assertEquals(System.getProperty("user.home"), settings.defaultProjectDir());
        assertFalse(settings.isDense());
    }

    @Test
    void roundTripPreservesAllFields(@TempDir Path dir) {
        var original = new ForemanSettings("/custom/projects", true);
        var file = dir.resolve("settings.json");
        repo.save(original, file);
        var loaded = repo.load(file);
        assertEquals("/custom/projects", loaded.defaultProjectDir());
        assertTrue(loaded.isDense());
    }

    @Test
    void denseDefaultsToFalseWhenMissingFromJson(@TempDir Path dir) throws Exception {
        var file = dir.resolve("settings.json");
        java.nio.file.Files.writeString(file, "{\"defaultProjectDir\":\"/old/path\"}");
        var loaded = repo.load(file);
        assertEquals("/old/path", loaded.defaultProjectDir());
        assertFalse(loaded.isDense());
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) {
        var settings = ForemanSettings.defaults();
        var file = dir.resolve("a/b/settings.json");
        repo.save(settings, file);
        assertTrue(file.toFile().exists());
    }
}
