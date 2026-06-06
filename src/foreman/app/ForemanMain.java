package foreman.app;

import com.formdev.flatlaf.FlatDarkLaf;
import foreman.ui.MainFrame;

import javax.swing.*;

public class ForemanMain {

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            var frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
