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

    @Test
    void buildLaunchCommandContainsCdAndClaude() {
        var cmd = MacOsTerminalLauncher.buildLaunchCommand("/home/user/myproject");
        assertTrue(cmd.contains("cd '/home/user/myproject'"));
        assertTrue(cmd.contains("$(cat '"));
        assertTrue(cmd.contains("last-briefing.txt"));
    }

    @Test
    void buildLaunchCommandEscapesPathSingleQuotes() {
        var cmd = MacOsTerminalLauncher.buildLaunchCommand("/home/user/it's project");
        assertTrue(cmd.contains("cd '/home/user/it'\\''s project'"));
    }
}
