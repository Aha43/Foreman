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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

public class SessionPanel extends JPanel {

    static final String HUMAN_ROLE_ID = "__human__";

    private final ForemanWorkspaceService workspaceService;
    private final SessionRegistry registry;
    private final TerminalLauncher launcher;
    private final BriefingService briefingService = new BriefingService();
    private final JPanel rowsPanel;

    private final Set<String> collapsedProjectIds = new HashSet<>();

    private String   selectedProjectId;
    private String   selectedRoleId;
    private Runnable selectedLaunchAction = () -> {};
    private Runnable selectedFocusAction  = () -> {};
    private Runnable selectedBriefAction  = () -> {};

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

        var im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var am = getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, 0), "row_launch");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "row_focus");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0), "row_brief");
        am.put("row_launch", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedLaunchAction.run(); }
        });
        am.put("row_focus", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedFocusAction.run(); }
        });
        am.put("row_brief", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { selectedBriefAction.run(); }
        });

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
            var humanLabel = sessionLabel(project.name(), "Human");
            registry.setRunning(project.id(), HUMAN_ROLE_ID, launcher.exists(humanLabel));
        }
    }

    private void rebuild() {
        selectedLaunchAction = () -> {};
        selectedFocusAction  = () -> {};
        selectedBriefAction  = () -> {};
        rowsPanel.removeAll();

        var projects = workspaceService.getWorkspace().projects();

        if (projects.isEmpty()) {
            var empty = new JLabel("No projects registered yet.");
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            empty.setBorder(new EmptyBorder(24, 16, 24, 16));
            rowsPanel.add(empty);
        } else {
            String lastId = null;
            for (var project : projects) {
                if (lastId != null) rowsPanel.add(Box.createVerticalStrut(8));
                lastId = project.id();
                rowsPanel.add(buildGroupHeader(project.id(), project.name()));
                rowsPanel.add(new JSeparator());
                if (!collapsedProjectIds.contains(project.id())) {
                    for (var assignment : project.team().assignments()) {
                        var row = new RowData(project.id(), project.name(), project.path(),
                                assignment.roleId(), assignment.label());
                        rowsPanel.add(buildRow(row));
                        rowsPanel.add(new JSeparator());
                    }
                    rowsPanel.add(buildHumanRow(project));
                    rowsPanel.add(new JSeparator());
                }
            }
        }

        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private JPanel buildGroupHeader(String projectId, String projectName) {
        var panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        var bg = UIManager.getColor("Table.alternateRowBackground");
        if (bg == null) bg = UIManager.getColor("Panel.background");
        panel.setBackground(bg);
        panel.setBorder(new EmptyBorder(6, 12, 6, 12));

        var label = new JLabel(projectName);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        panel.add(label, BorderLayout.WEST);

        var collapsed = collapsedProjectIds.contains(projectId);
        var chevron = new JLabel(collapsed ? "▶" : "▼");
        chevron.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(chevron, BorderLayout.EAST);

        var toggleCollapse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (collapsedProjectIds.contains(projectId)) collapsedProjectIds.remove(projectId);
                else collapsedProjectIds.add(projectId);
                rebuild();
            }
        };
        panel.addMouseListener(toggleCollapse);
        label.addMouseListener(toggleCollapse);
        chevron.addMouseListener(toggleCollapse);

        panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return panel;
    }

    private record RowData(String projectId, String projectName, String projectPath,
                           String roleId, String roleLabel) {}

    private JPanel buildRow(RowData row) {
        var session = registry.getSessions().stream()
                .filter(s -> s.projectId().equals(row.projectId()) && s.roleId().equals(row.roleId()))
                .findFirst();
        var isRunning = session.map(s -> s.active()).orElse(false);

        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var isSelected = row.projectId().equals(selectedProjectId)
                && row.roleId().equals(selectedRoleId);
        if (isSelected) panel.setBackground(UIManager.getColor("List.selectionBackground"));

        var roleLabel = new JLabel(row.roleLabel());
        if (isRunning) roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD));

        var briefButton = ForemanUiHelper.iconButton("Brief", ForemanUiHelper.icon("notes"));
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
                            new foreman.domain.Team(java.util.List.of()), null));
            var owner   = (Frame) SwingUtilities.getWindowAncestor(this);
            var briefing = briefingService.generate(project, role);
            BriefingDialog.show(owner, role.name(), project.name(), briefing);
        });
        if (isSelected) selectedBriefAction = briefButton::doClick;

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(roleLabel);

        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        if (launcher.isSupported()) {
            var statusLabel = new JLabel(isRunning ? "● Running" : "○ Stopped");
            statusLabel.setForeground(isRunning
                    ? UIManager.getColor("Component.accentColor")
                    : UIManager.getColor("Label.disabledForeground"));

            var actionButton = ForemanUiHelper.iconButton(
                    isRunning ? "Focus" : "Launch",
                    ForemanUiHelper.icon(isRunning ? "focus-2" : "terminal-2"));
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
                                    new foreman.domain.Team(java.util.List.of()), null));
                    var briefing = briefingService.generate(project, role);
                    launcher.launch(row.projectPath(), label, briefing, 1);
                    registry.setRunning(row.projectId(), row.roleId(), true);
                }
            });
            if (isSelected) {
                selectedLaunchAction = actionButton::doClick;
                selectedFocusAction  = isRunning ? actionButton::doClick : () -> {};
            }

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
            if (isSelected) {
                selectedLaunchAction = toggleButton::doClick;
                selectedFocusAction  = () -> {};
            }

            right.add(statusLabel);
            right.add(briefButton);
            right.add(toggleButton);
        }

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);

        var selectListener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selectedProjectId = row.projectId();
                selectedRoleId    = row.roleId();
                rebuild();
            }
        };
        panel.addMouseListener(selectListener);
        left.addMouseListener(selectListener);
        for (var c : left.getComponents()) c.addMouseListener(selectListener);

        return panel;
    }

    private JPanel buildHumanRow(Project project) {
        var session = registry.getSessions().stream()
                .filter(s -> s.projectId().equals(project.id()) && s.roleId().equals(HUMAN_ROLE_ID))
                .findFirst();
        var isRunning = session.map(s -> s.active()).orElse(false);

        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var isSelected = project.id().equals(selectedProjectId)
                && HUMAN_ROLE_ID.equals(selectedRoleId);
        if (isSelected) panel.setBackground(UIManager.getColor("List.selectionBackground"));

        var userIcon  = ForemanUiHelper.icon("user");
        var iconLabel = userIcon != null ? new JLabel(userIcon) : new JLabel();
        var textLabel = new JLabel("Human");
        if (isRunning) textLabel.setFont(textLabel.getFont().deriveFont(Font.BOLD));

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(iconLabel);
        left.add(textLabel);

        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        if (launcher.isSupported()) {
            var statusLabel = new JLabel(isRunning ? "● Running" : "○ Stopped");
            statusLabel.setForeground(isRunning
                    ? UIManager.getColor("Component.accentColor")
                    : UIManager.getColor("Label.disabledForeground"));

            var actionButton = ForemanUiHelper.iconButton(
                    isRunning ? "Focus" : "Launch",
                    ForemanUiHelper.icon(isRunning ? "focus-2" : "terminal-2"));
            actionButton.addActionListener(e -> {
                var label = sessionLabel(project.name(), "Human");
                if (isRunning) {
                    launcher.focus(label);
                } else {
                    launcher.launchShell(project.path(), label);
                    registry.setRunning(project.id(), HUMAN_ROLE_ID, true);
                }
            });
            if (isSelected) {
                selectedLaunchAction = actionButton::doClick;
                selectedFocusAction  = isRunning ? actionButton::doClick : () -> {};
            }

            right.add(statusLabel);
            right.add(actionButton);
        } else {
            var statusLabel = new JLabel(isRunning ? "● Active" : "○ Idle");
            statusLabel.setForeground(isRunning
                    ? UIManager.getColor("Component.accentColor")
                    : UIManager.getColor("Label.disabledForeground"));

            var toggleButton = new JButton(isRunning ? "Set Idle" : "Set Active");
            toggleButton.addActionListener(e -> registry.toggle(project.id(), HUMAN_ROLE_ID));
            if (isSelected) {
                selectedLaunchAction = toggleButton::doClick;
                selectedFocusAction  = () -> {};
            }

            right.add(statusLabel);
            right.add(toggleButton);
        }

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);

        var selectListener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selectedProjectId = project.id();
                selectedRoleId    = HUMAN_ROLE_ID;
                rebuild();
            }
        };
        panel.addMouseListener(selectListener);
        left.addMouseListener(selectListener);
        for (var c : left.getComponents()) c.addMouseListener(selectListener);

        return panel;
    }

    static String sessionLabel(String projectName, String roleLabel) {
        return "Foreman · " + projectName + " / " + roleLabel;
    }
}
