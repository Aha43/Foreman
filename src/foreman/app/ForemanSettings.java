package foreman.app;

public record ForemanSettings(String defaultProjectDir) {

    public static ForemanSettings defaults() {
        return new ForemanSettings(System.getProperty("user.home"));
    }
}
