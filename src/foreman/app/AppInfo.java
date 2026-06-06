package foreman.app;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AppInfo {
    public static final String NAME = "Foreman";
    public static final String VERSION = readVersion();

    private static String readVersion() {
        try (InputStream in = AppInfo.class.getResourceAsStream("/VERSION")) {
            if (in == null) return "dev";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (Exception e) {
            return "dev";
        }
    }
}
