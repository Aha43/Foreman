package foreman;

import foreman.app.AppInfo;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmokeTest {

    @Test
    void appNameIsForeman() {
        assertEquals("Foreman", AppInfo.NAME);
    }

    @Test
    void versionIsPresent() {
        assertNotNull(AppInfo.VERSION);
        assertFalse(AppInfo.VERSION.isBlank());
    }
}
