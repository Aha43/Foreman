package foreman.app;

public interface TerminalLauncher {
    void launch(String projectPath, String label, String briefing, int index);
    default void launchShell(String projectPath, String label) {}
    void focus(String label);
    boolean exists(String label);
    boolean isSupported();
    default String getTty(String label) { return null; }
}
