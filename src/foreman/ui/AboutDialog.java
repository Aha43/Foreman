package foreman.ui;

import foreman.app.AppInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

public class AboutDialog extends JDialog {

    public static void show(Frame owner) {
        new AboutDialog(owner).setVisible(true);
    }

    private AboutDialog(Frame owner) {
        super(owner, "About " + AppInfo.NAME, true);
        setResizable(false);

        var label = new JLabel(AppInfo.NAME + " " + AppInfo.VERSION + " — AI team manager");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(new EmptyBorder(24, 32, 16, 32));

        var closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        var footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        footer.add(closeBtn);

        var root = new JPanel(new BorderLayout());
        root.add(label, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(closeBtn);

        var mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_W, mask), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { dispose(); }
        });

        pack();
        setLocationRelativeTo(owner);
    }
}
