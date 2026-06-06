package foreman.ui;

import foreman.app.AppInfo;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame() {
        super(AppInfo.NAME + " " + AppInfo.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        var placeholder = new JLabel("Foreman — AI team manager", SwingConstants.CENTER);
        placeholder.setFont(placeholder.getFont().deriveFont(18f));
        add(placeholder, BorderLayout.CENTER);
    }
}
