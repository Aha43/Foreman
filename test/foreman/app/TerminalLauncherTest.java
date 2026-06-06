package foreman.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TerminalLauncherTest {

    @Test
    void noOpIsNotSupported() {
        assertFalse(new NoOpTerminalLauncher().isSupported());
    }

    @Test
    void noOpExistsReturnsFalse() {
        assertFalse(new NoOpTerminalLauncher().exists("any label"));
    }

    @Test
    void noOpLaunchDoesNotThrow() {
        assertDoesNotThrow(() -> new NoOpTerminalLauncher().launch("/path", "label", "briefing"));
    }

    @Test
    void noOpFocusDoesNotThrow() {
        assertDoesNotThrow(() -> new NoOpTerminalLauncher().focus("label"));
    }

    @Test
    void macOsIsSupportedOnMac() {
        var expected = System.getProperty("os.name", "").toLowerCase().contains("mac");
        assertEquals(expected, new MacOsTerminalLauncher().isSupported());
    }

    @Test
    void escapeShellLeavesCleanPathUnchanged() {
        assertEquals("/home/user/projects", MacOsTerminalLauncher.escapeShell("/home/user/projects"));
    }

    @Test
    void escapeShellHandlesSingleQuote() {
        assertEquals("it'\\''s", MacOsTerminalLauncher.escapeShell("it's"));
    }

    @Test
    void escapeShellHandlesMultipleSingleQuotes() {
        assertEquals("a'\\''b'\\''c", MacOsTerminalLauncher.escapeShell("a'b'c"));
    }
}
