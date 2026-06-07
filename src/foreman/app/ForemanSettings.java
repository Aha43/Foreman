package foreman.app;

public record ForemanSettings(String defaultProjectDir, Boolean dense) {

    public static ForemanSettings defaults() {
        return new ForemanSettings(System.getProperty("user.home"), false);
    }

    public boolean isDense() {
        return dense != null && dense;
    }
}
