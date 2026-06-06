package foreman.app;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MacOsTerminalLauncher implements TerminalLauncher {

    private static final String CLAUDE = resolveExecutable("claude");
    private static final Path BRIEFING_FILE =
            Path.of(System.getProperty("user.home"), ".foreman", "last-briefing.txt");

    private static String resolveExecutable(String name) {
        try {
            var proc = new ProcessBuilder("which", name).redirectErrorStream(true).start();
            var path = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            return path.isBlank() ? name : path;
        } catch (Exception e) {
            return name;
        }
    }

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public void launch(String projectPath, String label, String briefing) {
        copyToClipboard(briefing);
        var shellCmd = buildLaunchCommand(projectPath);
        var script = """
                tell application "Terminal"
                  set w to do script "%s"
                  set custom title of w to "%s"
                  activate
                end tell
                """.formatted(shellCmd, label);
        writeBriefing(briefing);
        runScript(script);
    }

    @Override
    public void focus(String label) {
        var script = """
                tell application "Terminal"
                  repeat with w in windows
                    if name of w contains "%s" then
                      set index of w to 1
                      activate
                      return
                    end if
                  end repeat
                end tell
                """.formatted(label);
        runScript(script);
    }

    @Override
    public boolean exists(String label) {
        var script = """
                tell application "Terminal"
                  repeat with w in windows
                    if name of w contains "%s" then
                      return "true"
                    end if
                  end repeat
                  return "false"
                end tell
                """.formatted(label);
        try {
            var proc = new ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start();
            var output = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            return "true".equals(output);
        } catch (Exception e) {
            return false;
        }
    }

    // package-private for testing
    static String escapeShell(String s) {
        return s.replace("'", "'\\''");
    }

    // package-private for testing
    static String buildLaunchCommand(String projectPath) {
        var escapedPath = escapeShell(projectPath);
        var escapedClaude = escapeShell(CLAUDE);
        var escapedBriefingFile = escapeShell(BRIEFING_FILE.toString());
        return "cd '%s' && '%s' \"$(cat '%s')\"".formatted(escapedPath, escapedClaude, escapedBriefingFile);
    }

    private void writeBriefing(String briefing) {
        try {
            Files.createDirectories(BRIEFING_FILE.getParent());
            Files.writeString(BRIEFING_FILE, briefing);
        } catch (IOException ignored) {
            // clipboard copy is the fallback
        }
    }

    private void copyToClipboard(String text) {
        var selection = new StringSelection(text);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }

    private void runScript(String script) {
        try {
            new ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ignored) {
            // best-effort; user can open terminal manually if osascript fails
        }
    }
}
