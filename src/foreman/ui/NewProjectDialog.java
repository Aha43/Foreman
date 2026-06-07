package foreman.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Path;
import java.util.Optional;

public class NewProjectDialog extends JDialog {

    public record Result(Path path, String name, String workflowPath) {}

    private final JTextField pathField         = new JTextField(30);
    private final JTextField nameField         = new JTextField(30);
    private final JTextField workflowPathField = new JTextField(30);
    private final JButton    okButton          = new JButton("Create");

    private Result result;

    private NewProjectDialog(Frame owner, Path initialDir) {
        super(owner, "New Project", true);
        workflowPathField.setEditable(false);
        okButton.setEnabled(false);

        var browseButton = new JButton("Browse…");
        browseButton.addActionListener(e -> browseProject(owner, initialDir));

        var browseWorkflowButton = new JButton("Browse…");
        browseWorkflowButton.addActionListener(e -> browseWorkflow(owner));

        var docListener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { syncOk(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { syncOk(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { syncOk(); }
        };
        pathField.getDocument().addDocumentListener(docListener);
        nameField.getDocument().addDocumentListener(docListener);

        var cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        okButton.addActionListener(e -> {
            var wf = workflowPathField.getText().strip();
            result = new Result(
                    Path.of(pathField.getText().strip()),
                    nameField.getText().strip(),
                    wf.isBlank() ? null : wf);
            dispose();
        });

        var form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(12, 12, 4, 12));
        var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(4, 0, 4, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Path:"), gbc);
        var pathRow = new JPanel(new BorderLayout(6, 0));
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseButton, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(pathRow, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        var workflowLabel = new JLabel("Workflow path:");
        workflowLabel.setToolTipText("Optional: separate directory for role docs (leave blank to use the project directory)");
        form.add(workflowLabel, gbc);
        var workflowRow = new JPanel(new BorderLayout(6, 0));
        workflowRow.add(workflowPathField, BorderLayout.CENTER);
        workflowRow.add(browseWorkflowButton, BorderLayout.EAST);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(workflowRow, gbc);

        var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        buttons.add(cancelButton);
        buttons.add(okButton);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(okButton);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void browseProject(Frame owner, Path initialDir) {
        var chooser = new JFileChooser(initialDir != null ? initialDir.toFile() : null);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select or create project directory");
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            var dir = chooser.getSelectedFile();
            pathField.setText(dir.getAbsolutePath());
            if (nameField.getText().isBlank()) {
                nameField.setText(dir.getName());
            }
            syncOk();
        }
    }

    private void browseWorkflow(Frame owner) {
        var initial = workflowPathField.getText().isBlank() ? null
                : new java.io.File(workflowPathField.getText());
        var chooser = new JFileChooser(initial);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select workflow path (sidecar directory)");
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            workflowPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void syncOk() {
        okButton.setEnabled(!pathField.getText().isBlank() && !nameField.getText().isBlank());
    }

    public static Optional<Result> show(Frame owner, Path initialDir) {
        var dialog = new NewProjectDialog(owner, initialDir);
        dialog.setVisible(true);
        return Optional.ofNullable(dialog.result);
    }
}
