package foreman.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

public class ShortcutsDialog extends JDialog {

    private static final boolean MAC =
            System.getProperty("os.name").toLowerCase().contains("mac");

    public static void show(Frame owner) {
        new ShortcutsDialog(owner).setVisible(true);
    }

    private ShortcutsDialog(Frame owner) {
        super(owner, "Keyboard Shortcuts", true);
        setResizable(false);

        var body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 20, 8, 20));

        body.add(sectionHeader("Sessions"));
        body.add(Box.createVerticalStrut(6));
        body.add(shortcutRow("L",     "Launch selected session"));
        body.add(shortcutRow("F",     "Focus selected session"));
        body.add(shortcutRow("B",     "Brief selected session"));
        body.add(Box.createVerticalStrut(14));
        body.add(sectionHeader("General"));
        body.add(Box.createVerticalStrut(6));
        body.add(shortcutRow(sc("N"), "New Project"));
        body.add(shortcutRow(sc("R"), "Register Project"));
        body.add(shortcutRow(sc(","), "Settings"));
        body.add(shortcutRow(sc("/"), "Keyboard Shortcuts"));
        body.add(shortcutRow(sc("W"), "Close dialog"));
        body.add(shortcutRow(sc("Q"), "Exit"));
        body.add(shortcutRow("F1",    "Help"));
        body.add(Box.createVerticalStrut(8));

        var closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> dispose());

        var footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        footer.add(closeBtn);

        var root = new JPanel(new BorderLayout());
        root.add(body, BorderLayout.CENTER);
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

    private static JComponent sectionHeader(String text) {
        var lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setForeground(UIManager.getColor("Label.disabledForeground"));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private static JPanel shortcutRow(String shortcut, String description) {
        var row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 1));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(chip(shortcut));
        row.add(new JLabel(description));
        return row;
    }

    private static JLabel chip(String text) {
        var lbl = new JLabel(text);
        lbl.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        var borderColor = UIManager.getColor("Component.borderColor");
        if (borderColor == null) borderColor = Color.GRAY;
        lbl.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(1, 5, 1, 5)));
        return lbl;
    }

    static String sc(String key) {
        return MAC ? "⌘" + key : "Ctrl+" + key;
    }
}
