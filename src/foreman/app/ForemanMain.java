package foreman.app;

import com.formdev.flatlaf.FlatDarkLaf;
import foreman.ui.ForemanUiHelper;
import foreman.ui.MainFrame;

import javax.swing.*;
import java.util.Arrays;

public class ForemanMain {

    public static void main(String[] args) {
        var devMode          = Arrays.asList(args).contains("--dev");
        var workspaceService = new ForemanWorkspaceService(devMode);
        var settingsService  = new ForemanSettingsService(devMode);
        var launcher         = System.getProperty("os.name", "").toLowerCase().contains("mac")
                ? (TerminalLauncher) new MacOsTerminalLauncher()
                : new NoOpTerminalLauncher();
        ForemanUiHelper.init(settingsService.get().isDense());
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            var frame = new MainFrame(workspaceService, settingsService, launcher);
            frame.setVisible(true);
        });
    }
}
