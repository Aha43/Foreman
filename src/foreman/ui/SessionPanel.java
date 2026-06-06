package foreman.ui;

import foreman.app.BriefingService;
import foreman.app.ForemanWorkspaceService;
import foreman.app.SessionRegistry;
import foreman.app.TerminalLauncher;
import foreman.domain.Project;
import foreman.domain.Role;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class SessionPanel extends JPanel {

    private final ForemanWorkspaceService workspaceService;
    private final SessionRegistry registry;
    private final TerminalLauncher launcher;
    private final BriefingService briefingService = new BriefingService();
    private final JPanel rowsPanel;

    public SessionPanel(ForemanWorkspaceService workspaceService, SessionRegistry registry,
                        TerminalLauncher launcher) {
        super(new BorderLayout());
        this.workspaceService = workspaceService;
        this.registry = registry;
        this.launcher = launcher;

        rowsPanel = new JPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));

        var scroll = new JScrollPane(rowsPanel);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        registry.onChange(this::rebuild);
        rebuild();
    }

    public void reload() {
        if (launcher.isSupported()) restoreRunningState();
        rebuild();
    }

    private void restoreRunningState() {
        for (var project : workspaceService.getWorkspace().projects()) {
            for (var assignment : project.team().assignments()) {
                var label = sessionLabel(project.name(), assignment.label());
                registry.setRunning(project.id(), assignment.roleId(), launcher.exists(label));
            }
        }
    }

    private void rebuild() {
        rowsPanel.removeAll();

        var projects = workspaceService.getWorkspace().projects();
        var rows = collectRows(projects);

        if (rows.isEmpty()) {
            var empty = new JLabel("No sessions registered. Select a project and mark a role as active.");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            empty.setBorder(new EmptyBorder(24, 16, 24, 16));
            rowsPanel.add(empty);
        } else {
            for (var row : rows) {
                rowsPanel.add(buildRow(row));
                rowsPanel.add(new JSeparator());
            }
        }

        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private record RowData(String projectId, String projectName, String projectPath,
                           String roleId, String roleLabel) {}

    private java.util.List<RowData> collectRows(java.util.List<Project> projects) {
        var rows = new ArrayList<RowData>();
        for (var project : projects) {
            for (var assignment : project.team().assignments()) {
                rows.add(new RowData(project.id(), project.name(), project.path(),
                        assignment.roleId(), assignment.label()));
            }
        }
        return rows;
    }

    private JPanel buildRow(RowData row) {
        var session = registry.getSessions().stream()
                .filter(s -> s.projectId().equals(row.projectId()) && s.roleId().equals(row.roleId()))
                .findFirst();
        var isRunning = session.map(s -> s.active()).orElse(false);

        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var projectLabel = new JLabel(row.projectName());
        var roleLabel    = new JLabel(row.roleLabel());

        if (isRunning) {
            projectLabel.setFont(projectLabel.getFont().deriveFont(Font.BOLD));
            roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD));
        }

        var briefButton = new JButton("Brief");
        briefButton.addActionListener(e -> {
            var workspace = workspaceService.getWorkspace();
            var role = workspace.roles().stream()
                    .filter(r -> r.id().equals(row.roleId()))
                    .findFirst()
                    .orElseGet(() -> new Role(row.roleId(), row.roleLabel(), "", null));
            var project = workspace.projects().stream()
                    .filter(p -> p.id().equals(row.projectId()))
                    .findFirst()
                    .orElseGet(() -> new Project(
                            row.projectId(), row.projectName(), row.projectPath(), "",
                            new foreman.domain.Team(java.util.List.of())));
            var owner   = (Frame) SwingUtilities.getWindowAncestor(this);
            var briefing = briefingService.generate(project, role);
            BriefingDialog.show(owner, role.name(), project.name(), briefing);
        });

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(projectLabel);
        left.add(new JLabel("/"));
        left.add(roleLabel);

        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        if (launcher.isSupported()) {
            var statusLabel = new JLabel(isRunning ? "● Running" : "○ Stopped");
            statusLabel.setForeground(isRunning
                    ? UIManager.getColor("Component.accentColor")
                    : UIManager.getColor("Label.disabledForeground"));

            var actionButton = new JButton(isRunning ? "Focus" : "Launch");
            actionButton.addActionListener(e -> {
                var label = sessionLabel(row.projectName(), row.roleLabel());
                if (isRunning) {
                    launcher.focus(label);
                } else {
                    var workspace = workspaceService.getWorkspace();
                    var role = workspace.roles().stream()
                            .filter(r -> r.id().equals(row.roleId()))
                            .findFirst()
                            .orElseGet(() -> new Role(row.roleId(), row.roleLabel(), "", null));
                    var project = workspace.projects().stream()
                            .filter(p -> p.id().equals(row.projectId()))
                            .findFirst()
                            .orElseGet(() -> new Project(
                                    row.projectId(), row.projectName(), row.projectPath(), "",
                                    new foreman.domain.Team(java.util.List.of())));
                    var briefing = briefingService.generate(project, role);
                    launcher.launch(row.projectPath(), label, briefing);
                    registry.setRunning(row.projectId(), row.roleId(), true);
                }
            });

            right.add(statusLabel);
            right.add(briefButton);
            right.add(actionButton);
        } else {
            var statusLabel = new JLabel(isRunning ? "● Active" : "○ Idle");
            statusLabel.setForeground(isRunning
                    ? UIManager.getColor("Component.accentColor")
                    : UIManager.getColor("Label.disabledForeground"));

            var toggleButton = new JButton(isRunning ? "Set Idle" : "Set Active");
            toggleButton.addActionListener(e -> registry.toggle(row.projectId(), row.roleId()));

            right.add(statusLabel);
            right.add(briefButton);
            right.add(toggleButton);
        }

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);

        return panel;
    }

    static String sessionLabel(String projectName, String roleLabel) {
        return "Foreman · " + projectName + " / " + roleLabel;
    }
}
