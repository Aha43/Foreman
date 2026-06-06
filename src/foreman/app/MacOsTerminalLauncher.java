package foreman.app;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MacOsTerminalLauncher implements TerminalLauncher {

    private static final String CLAUDE = resolveExecutable("claude");
    private static final Path FOREMAN_DIR   = Path.of(System.getProperty("user.home"), ".foreman");
    private static final Path BRIEFING_FILE = FOREMAN_DIR.resolve("last-briefing.txt");
    private static final Path LAUNCH_SCRIPT = FOREMAN_DIR.resolve("launch.sh");

    // label → TTY device (e.g. /dev/ttys005); in-memory only — TTYs are recycled
    // by macOS across restarts so persisting them causes stale matches.
    private final Map<String, String> ttyMap = new LinkedHashMap<>();

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
        var escapedScript = escapeShell(LAUNCH_SCRIPT.toString());
        // Return the TTY so we can identify the tab later regardless of what
        // Claude Code does to the visible window title.
        var script = """
                tell application "Terminal"
                  set w to do script "bash '%s'"
                  set custom title of w to "%s"
                  activate
                  return tty of w
                end tell
                """.formatted(escapedScript, label);
        var tty = runScriptCapturing(script).strip();
        if (!tty.isBlank()) {
            ttyMap.put(label, tty);
        }
    }

    @Override
    public void focus(String label) {
        var tty = ttyMap.get(label);
        if (tty == null) return;
        var script = """
                tell application "Terminal"
                  repeat with w in windows
                    repeat with t in tabs of w
                      if tty of t is "%s" then
                        set index of w to 1
                        set selected tab of w to t
                        activate
                        return
                      end if
                    end repeat
                  end repeat
                end tell
                """.formatted(tty);
        runScript(script);
    }

    @Override
    public boolean exists(String label) {
        var tty = ttyMap.get(label);
        if (tty == null) return false;
        var script = """
                tell application "Terminal"
                  repeat with w in windows
                    repeat with t in tabs of w
                      if tty of t is "%s" then
                        return "true"
                      end if
                    end repeat
                  end repeat
                  return "false"
                end tell
                """.formatted(tty);
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
    static String buildLaunchScript(String projectPath) {
        return "#!/bin/bash\n"
                + "cd '" + escapeShell(projectPath) + "'\n"
                + "exec '" + escapeShell(CLAUDE) + "' \"$(cat '" + escapeShell(BRIEFING_FILE.toString()) + "')\"\n";
    }

    private String runScriptCapturing(String script) {
        try {
            var proc = new ProcessBuilder("osascript", "-e", script)
                    .redirectErrorStream(true)
                    .start();
            var output = new String(proc.getInputStream().readAllBytes()).strip();
            proc.waitFor();
            return output;
        } catch (Exception e) {
            return "";
        }
    }

    private void writeBriefing(String briefing) {
        try {
            Files.createDirectories(FOREMAN_DIR);
            Files.writeString(BRIEFING_FILE, briefing);
        } catch (IOException ignored) {
            // clipboard copy is the fallback
        }
    }

    private void writeLaunchScript(String projectPath) {
        try {
            Files.createDirectories(FOREMAN_DIR);
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
