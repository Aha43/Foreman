package foreman.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

public class BriefingDialog extends JDialog {

    private BriefingDialog(Frame owner, String title, String briefing) {
        super(owner, title, true);

        var textArea = new JTextArea(briefing);
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        var scroll = new JScrollPane(textArea);
        scroll.setPreferredSize(new Dimension(560, 300));

        var copyButton  = new JButton("Copy to Clipboard");
        var closeButton = new JButton("Close");
        copyButton.addActionListener(e -> {
            var sel = new StringSelection(briefing);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            copyButton.setText("Copied!");
            copyButton.setEnabled(false);
        });
        closeButton.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(closeButton);

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.add(copyButton);
        buttons.add(closeButton);

        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    public static void show(Frame owner, String roleName, String projectName, String briefing) {
        new BriefingDialog(owner, "Brief: " + roleName + " on " + projectName, briefing)
                .setVisible(true);
    }
}
