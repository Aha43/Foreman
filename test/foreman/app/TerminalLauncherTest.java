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
        assertDoesNotThrow(() -> new NoOpTerminalLauncher().launch("/path", "label", "briefing", 1));
    }

    @Test
    void noOpLaunchWithHighIndexDoesNotThrow() {
        assertDoesNotThrow(() -> new NoOpTerminalLauncher().launch("/path", "label", "briefing", 3));
    }

    @Test
    void noOpGetTtyReturnsNull() {
        assertNull(new NoOpTerminalLauncher().getTty("any label"));
    }

    @Test
    void macOsGetTtyReturnsNullWhenNoTtyKnown() {
        assertNull(new MacOsTerminalLauncher().getTty("Foreman · Unknown / Role"));
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
    void macOsExistsReturnsFalseWhenNoTtyKnown() {
        // No launch has been called, so no TTY is stored for this label.
        assertFalse(new MacOsTerminalLauncher().exists("Foreman · Unknown / Role"));
    }

    @Test
    void macOsFocusDoesNotThrowWhenNoTtyKnown() {
        assertDoesNotThrow(() -> new MacOsTerminalLauncher().focus("Foreman · Unknown / Role"));
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
    void noOpLaunchShellDoesNotThrow() {
        assertDoesNotThrow(() -> new NoOpTerminalLauncher().launchShell("/path", "label"));
    }

    @Test
    void buildLaunchScriptContainsCdAndExec() {
        var script = MacOsTerminalLauncher.buildLaunchScript("/home/user/myproject");
        assertTrue(script.startsWith("#!/bin/bash\n"));
        assertTrue(script.contains("cd '/home/user/myproject'"));
        assertTrue(script.contains("exec '"));
        assertTrue(script.contains("last-briefing.txt"));
    }

    @Test
    void buildLaunchScriptEscapesPathSingleQuotes() {
        var script = MacOsTerminalLauncher.buildLaunchScript("/home/user/it's project");
        assertTrue(script.contains("cd '/home/user/it'\\''s project'"));
    }

    @Test
    void buildLaunchScriptContainsNoBareDoubleQuotesAroundSubstitution() {
        // double quotes around $(cat ...) must stay inside the bash script,
        // not break the AppleScript string that wraps bash '/launch.sh'
        var script = MacOsTerminalLauncher.buildLaunchScript("/home/user/project");
        assertTrue(script.contains("\"$(cat '"));
    }
}
