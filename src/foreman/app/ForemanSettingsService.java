package foreman.app;

import java.nio.file.Path;

public class ForemanSettingsService {

    private static final Path PROD_PATH = Path.of(System.getProperty("user.home"), ".foreman", "settings.json");
    private static final Path DEV_PATH  = Path.of(System.getProperty("user.home"), ".foreman", "dev", "settings.json");

    private final Path settingsPath;
    private final ForemanSettingsRepository repository = new ForemanSettingsRepository();
    private ForemanSettings settings;

    public ForemanSettingsService(boolean devMode) {
        this.settingsPath = devMode ? DEV_PATH : PROD_PATH;
        this.settings = repository.load(settingsPath);
    }

    public ForemanSettings get() {
        return settings;
    }

    public void update(ForemanSettings updated) {
        this.settings = updated;
        repository.save(settings, settingsPath);
    }
}
