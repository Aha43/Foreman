package foreman.ui;

import foreman.app.BriefingService;
import foreman.app.ForemanWorkspaceService;
import foreman.domain.Project;
import foreman.domain.RoleAssignment;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ProjectDetailPanel extends JPanel {

    private final ForemanWorkspaceService workspaceService;
    private final BriefingService         briefingService = new BriefingService();

    private final JLabel    nameValue         = new JLabel();
    private final JLabel    pathValue         = new JLabel();
    private final JLabel    workflowPathValue = new JLabel();
    private final JLabel    workflowPathLabel = new JLabel("Workflow path:");
    private final JTextArea descValue         = new JTextArea();

    private final DefaultListModel<String> assignmentsModel = new DefaultListModel<>();
    private final JList<String>            assignmentsList  = new JList<>(assignmentsModel);

    private final JLabel    roleContentHeader = new JLabel(" ");
    private final JTextArea roleContentArea   = new JTextArea();

    private Project              currentProject;
    private List<RoleAssignment> currentAssignments = List.of();

    public ProjectDetailPanel(ForemanWorkspaceService workspaceService) {
        super(new BorderLayout(0, 0));
        this.workspaceService = workspaceService;
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

        assignmentsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        assignmentsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) onRoleSelected();
        });

        var teamLabel = new JLabel("Team:");
        var teamPanel = new JPanel(new BorderLayout(0, 4));
        teamPanel.setOpaque(false);
        teamPanel.add(teamLabel, BorderLayout.NORTH);
        teamPanel.add(new JScrollPane(assignmentsList), BorderLayout.CENTER);

        roleContentArea.setEditable(false);
        roleContentArea.setLineWrap(true);
        roleContentArea.setWrapStyleWord(true);
        roleContentArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        roleContentHeader.setFont(roleContentHeader.getFont().deriveFont(Font.BOLD));
        roleContentHeader.setBorder(new EmptyBorder(0, 0, 4, 0));

        var roleContentScroll = new JScrollPane(roleContentArea);
        var rolePanel = new JPanel(new BorderLayout(0, 4));
        rolePanel.setOpaque(false);
        rolePanel.add(roleContentHeader, BorderLayout.NORTH);
        rolePanel.add(roleContentScroll, BorderLayout.CENTER);

        var centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, teamPanel, rolePanel);
        centerSplit.setDividerLocation(120);
        centerSplit.setDividerSize(4);
        centerSplit.setOpaque(false);

        add(fields, BorderLayout.NORTH);
        add(centerSplit, BorderLayout.CENTER);

        clearRoleContent();
    }

    private void onRoleSelected() {
        var idx = assignmentsList.getSelectedIndex();
        if (idx < 0 || currentProject == null) {
            clearRoleContent();
            return;
        }
        var assignment = currentAssignments.get(idx);
        var role = workspaceService.getWorkspace().roles().stream()
                .filter(r -> r.id().equals(assignment.roleId()))
                .findFirst()
                .orElse(null);
        if (role == null) {
            roleContentHeader.setText(assignment.label());
            roleContentArea.setText("Role definition not found in workspace.");
            return;
        }
        var workflowRoot = Path.of(currentProject.effectiveWorkflowPath());
        var docPath = briefingService.findRoleDoc(workflowRoot, role);
        roleContentHeader.setText(role.name());
        roleContentArea.setForeground(UIManager.getColor("TextArea.foreground"));
        if (docPath.isPresent()) {
            try {
                roleContentArea.setText(Files.readString(workflowRoot.resolve(docPath.get())));
                roleContentArea.setCaretPosition(0);
                return;
            } catch (IOException e) {
                roleContentArea.setText("Could not read role file: " + e.getMessage());
                return;
            }
        }
        if (role.instructions() != null && !role.instructions().isBlank()) {
            roleContentArea.setText(role.instructions());
            roleContentArea.setCaretPosition(0);
        } else {
            roleContentArea.setText("Role file not found — try Rescan");
        }
    }

    private void clearRoleContent() {
        roleContentHeader.setText(" ");
        roleContentArea.setForeground(UIManager.getColor("Label.disabledForeground"));
        roleContentArea.setText("Select a role to view its instructions.");
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
        currentProject = project;
        nameValue.setText(project.name());
        pathValue.setText(project.path());
        var hasSidecar = project.workflowPath() != null && !project.workflowPath().isBlank();
        workflowPathLabel.setVisible(hasSidecar);
        workflowPathValue.setVisible(hasSidecar);
        workflowPathValue.setText(hasSidecar ? project.workflowPath() : "");
        descValue.setText(project.description());
        currentAssignments = project.team().assignments();
        assignmentsModel.clear();
        currentAssignments.forEach(a -> assignmentsModel.addElement(a.label()));
        assignmentsList.clearSelection();
        clearRoleContent();
    }

    public void clearProject() {
        currentProject = null;
        currentAssignments = List.of();
        nameValue.setText("");
        pathValue.setText("");
        workflowPathLabel.setVisible(false);
        workflowPathValue.setVisible(false);
        workflowPathValue.setText("");
        descValue.setText("");
        assignmentsModel.clear();
        assignmentsList.clearSelection();
        clearRoleContent();
    }
}
