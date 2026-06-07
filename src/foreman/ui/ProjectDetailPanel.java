package foreman.ui;

import foreman.domain.Project;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ProjectDetailPanel extends JPanel {

    private final JLabel nameValue         = new JLabel();
    private final JLabel pathValue         = new JLabel();
    private final JLabel workflowPathValue = new JLabel();
    private final JLabel workflowPathLabel = new JLabel("Workflow path:");
    private final JTextArea descValue      = new JTextArea();
    private final DefaultListModel<String> assignmentsModel = new DefaultListModel<>();

    public ProjectDetailPanel() {
        super(new BorderLayout(0, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        workflowPathLabel.setVisible(false);
        workflowPathValue.setVisible(false);

        descValue.setLineWrap(true);
        descValue.setWrapStyleWord(true);
        descValue.setEditable(false);
        descValue.setOpaque(false);

        var fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        var gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(3, 0, 3, 10);

        addRow(fields, gbc, 0, "Name:",        nameValue);
        addRow(fields, gbc, 1, "Path:",        pathValue);
        addRow(fields, gbc, 2, workflowPathLabel, workflowPathValue);
        addRow(fields, gbc, 3, "Description:", descValue);

        var assignmentsList = new JList<>(assignmentsModel);
        assignmentsList.setOpaque(false);
        assignmentsList.setEnabled(false);

        var teamPanel = new JPanel(new BorderLayout(0, 4));
        teamPanel.setOpaque(false);
        teamPanel.add(new JLabel("Team:"), BorderLayout.NORTH);
        teamPanel.add(new JScrollPane(assignmentsList), BorderLayout.CENTER);

        add(fields, BorderLayout.NORTH);
        add(teamPanel, BorderLayout.CENTER);
    }

    private void addRow(JPanel parent, GridBagConstraints gbc, int row, String labelText, JComponent value) {
        addRow(parent, gbc, row, new JLabel(labelText), value);
    }

    private void addRow(JPanel parent, GridBagConstraints gbc, int row, JLabel label, JComponent value) {
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        parent.add(label, gbc);
        gbc.gridx = 1; gbc.weightx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        parent.add(value, gbc);
    }

    public void showProject(Project project) {
        nameValue.setText(project.name());
        pathValue.setText(project.path());
        var hasSidecar = project.workflowPath() != null && !project.workflowPath().isBlank();
        workflowPathLabel.setVisible(hasSidecar);
        workflowPathValue.setVisible(hasSidecar);
        workflowPathValue.setText(hasSidecar ? project.workflowPath() : "");
        descValue.setText(project.description());
        assignmentsModel.clear();
        project.team().assignments().forEach(a -> assignmentsModel.addElement(a.label()));
    }

    public void clearProject() {
        nameValue.setText("");
        pathValue.setText("");
        workflowPathLabel.setVisible(false);
        workflowPathValue.setVisible(false);
        workflowPathValue.setText("");
        descValue.setText("");
        assignmentsModel.clear();
    }
}
