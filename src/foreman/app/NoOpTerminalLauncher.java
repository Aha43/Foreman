package foreman.app;

public class NoOpTerminalLauncher implements TerminalLauncher {
    @Override public void launch(String projectPath, String label, String briefing, int index) {}
    @Override public void launchShell(String projectPath, String label) {}
    @Override public void focus(String label) {}
    @Override public boolean exists(String label) { return false; }
    @Override public boolean isSupported() { return false; }
}
