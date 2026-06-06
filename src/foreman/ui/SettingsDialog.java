package foreman.ui;

import foreman.app.ForemanSettings;
import foreman.app.ForemanSettingsService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SettingsDialog extends JDialog {

    private SettingsDialog(Frame owner, ForemanSettingsService settingsService) {
        super(owner, "Settings", true);

        var current   = settingsService.get();
        var dirField  = new JTextField(current.defaultProjectDir(), 30);
        var denseCheck = new JCheckBox("Dense mode (icon-only buttons)");
        denseCheck.setSelected(current.isDense());

        var browseButton = new JButton("Browse…");
        browseButton.addActionListener(e -> {
            var chooser = new JFileChooser(dirField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select default project directory");
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        var okButton     = new JButton("OK");
        var cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        okButton.addActionListener(e -> {
            var dense = denseCheck.isSelected();
            settingsService.update(new ForemanSettings(dirField.getText().strip(), dense));
            ForemanUiHelper.applyDense(dense);
            dispose();
        });
        getRootPane().setDefaultButton(okButton);

        var form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 4, 12));
        var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 0, 4, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Default project directory:"), gbc);
        var dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.add(dirField, BorderLayout.CENTER);
        dirRow.add(browseButton, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(dirRow, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.NONE;
        form.add(denseCheck, gbc);

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.add(cancelButton);
        buttons.add(okButton);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    public static void show(Frame owner, ForemanSettingsService settingsService) {
        new SettingsDialog(owner, settingsService).setVisible(true);
    }
}
