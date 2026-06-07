package foreman.app;

public record ForemanSettings(
        String  defaultProjectDir,
        Boolean dense,
        Integer helpDialogX,
        Integer helpDialogY,
        Integer helpDialogWidth,
        Integer helpDialogHeight) {

    public static ForemanSettings defaults() {
        return new ForemanSettings(System.getProperty("user.home"), false, null, null, null, null);
    }

    public boolean isDense() {
        return dense != null && dense;
    }

    public ForemanSettings withDefaultProjectDir(String dir) {
        return new ForemanSettings(dir, dense, helpDialogX, helpDialogY, helpDialogWidth, helpDialogHeight);
    }

    public ForemanSettings withDense(boolean dense) {
        return new ForemanSettings(defaultProjectDir, dense, helpDialogX, helpDialogY, helpDialogWidth, helpDialogHeight);
    }

    public ForemanSettings withHelpDialogBounds(int x, int y, int width, int height) {
        return new ForemanSettings(defaultProjectDir, dense, x, y, width, height);
    }
}
