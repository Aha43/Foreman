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
    private static final Path LAUNCH_SCRIPT =
            Path.of(System.getProperty("user.home"), ".foreman", "launch.sh");

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
        writeBriefing(briefing);
        writeLaunchScript(projectPath);
        // AppleScript do script runs bash with the launch script — no double quotes
        // need to be embedded in the AppleScript string, avoiding the escaping problem.
        var escapedScript = escapeShell(LAUNCH_SCRIPT.toString());
        var script = """
                tell application "Terminal"
                  set w to do script "bash '%s'"
                  set custom title of w to "%s"
                  activate
                end tell
                """.formatted(escapedScript, label);
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

    // package-private for testing — generates the bash script content written to launch.sh
    static String buildLaunchScript(String projectPath) {
        return "#!/bin/bash\n"
                + "cd '" + escapeShell(projectPath) + "'\n"
                + "exec '" + escapeShell(CLAUDE) + "' \"$(cat '" + escapeShell(BRIEFING_FILE.toString()) + "')\"\n";
    }

    private void writeBriefing(String briefing) {
        try {
            Files.createDirectories(BRIEFING_FILE.getParent());
            Files.writeString(BRIEFING_FILE, briefing);
        } catch (IOException ignored) {
            // clipboard copy is the fallback
        }
    }

    private void writeLaunchScript(String projectPath) {
        try {
            Files.createDirectories(LAUNCH_SCRIPT.getParent());
            Files.writeString(LAUNCH_SCRIPT, buildLaunchScript(projectPath));
            LAUNCH_SCRIPT.toFile().setExecutable(true);
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
