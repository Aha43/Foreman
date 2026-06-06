package foreman.app;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;

public class MacOsTerminalLauncher implements TerminalLauncher {

    @Override
    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    @Override
    public void launch(String projectPath, String label, String briefing) {
        copyToClipboard(briefing);
        var script = """
                tell application "Terminal"
                  set w to do script "cd '%s' && clear"
                  set custom title of w to "%s"
                  activate
                end tell
                """.formatted(escapeShell(projectPath), label);
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
