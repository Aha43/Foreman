package foreman.app;

public class NoOpTerminalLauncher implements TerminalLauncher {
    @Override public void launch(String projectPath, String label, String briefing) {}
    @Override public void focus(String label) {}
    @Override public boolean exists(String label) { return false; }
    @Override public boolean isSupported() { return false; }
}
