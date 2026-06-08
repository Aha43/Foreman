package foreman.ui;

import foreman.app.BriefingService;
import foreman.app.ForemanWorkspaceService;
import foreman.app.SessionRegistry;
import foreman.app.TerminalLauncher;
import foreman.domain.Project;
import foreman.domain.Role;
import foreman.domain.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
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
    private String   selectedSessionId;  // null = role-level selection; non-null = sub-row
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
                for (var s : registry.getSessionsForRole(project.id(), assignment.roleId())) {
                    var label = fullSessionLabel(project.name(), assignment.label(), s.index());
                    // Only stop if the TTY is known and confirmed gone. If getTty returns null
                    // the launch never stored a TTY (osascript failure) — leave the session in
                    // place so a running terminal isn't incorrectly cleared.
                    if (launcher.getTty(label) != null && !launcher.exists(label))
                        registry.stopSession(s.id());
                }
            }
            for (var s : registry.getSessionsForRole(project.id(), HUMAN_ROLE_ID)) {
                var label = sessionLabel(project.name(), "Human");
                if (launcher.getTty(label) != null && !launcher.exists(label))
                    registry.stopSession(s.id());
            }
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
        var runningSessions = registry.getSessionsForRole(row.projectId(), row.roleId())
                .stream().filter(Session::active).toList();
        if (runningSessions.isEmpty())    return buildStoppedRow(row);
        if (runningSessions.size() == 1) return buildSingleRunningRow(row, runningSessions.get(0));
        return buildMultiRunningRow(row, runningSessions);
    }

    private JPanel buildStoppedRow(RowData row) {
        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var isSelected = row.projectId().equals(selectedProjectId)
                && row.roleId().equals(selectedRoleId) && selectedSessionId == null;
        if (isSelected) panel.setBackground(UIManager.getColor("List.selectionBackground"));

        var roleLabel = new JLabel(row.roleLabel());

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(roleLabel);

        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        var briefButton = buildBriefButton(row);
        if (isSelected) selectedBriefAction = briefButton::doClick;

        if (launcher.isSupported()) {
            var statusLabel = new JLabel("○ Stopped");
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            var launchButton = ForemanUiHelper.iconButton("Launch", ForemanUiHelper.icon("terminal-2"));
            launchButton.addActionListener(e -> {
                var s = registry.launchSession(row.projectId(), row.roleId());
                var briefing = generateBriefing(row);
                launcher.launch(row.projectPath(), sessionLabel(row.projectName(), row.roleLabel()),
                        briefing, s.index());
            });
            if (isSelected) {
                selectedLaunchAction = launchButton::doClick;
                selectedFocusAction  = () -> {};
            }
            right.add(statusLabel);
            right.add(briefButton);
            right.add(launchButton);
        } else {
            var statusLabel = new JLabel("○ Idle");
            statusLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

            var toggleButton = new JButton("Set Active");
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
        addRoleSelectListener(panel, left, row.projectId(), row.roleId());
        return panel;
    }

    private JPanel buildSingleRunningRow(RowData row, Session session) {
        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var isSelected = row.projectId().equals(selectedProjectId)
                && row.roleId().equals(selectedRoleId) && selectedSessionId == null;
        if (isSelected) panel.setBackground(UIManager.getColor("List.selectionBackground"));

        var roleLabel = new JLabel(row.roleLabel());
        roleLabel.setFont(roleLabel.getFont().deriveFont(Font.BOLD));

        var left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(roleLabel);

        var right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        var briefButton = buildBriefButton(row);
        if (isSelected) selectedBriefAction = briefButton::doClick;

        if (launcher.isSupported()) {
            var statusLabel = new JLabel("● Running");
            statusLabel.setForeground(UIManager.getColor("Component.accentColor"));

            var fullLabel = fullSessionLabel(row.projectName(), row.roleLabel(), session.index());
            var focusButton = ForemanUiHelper.iconButton("Focus", ForemanUiHelper.icon("focus-2"));
            focusButton.addActionListener(e -> launcher.focus(fullLabel));

            var anotherButton = ForemanUiHelper.iconButton("+ Another", ForemanUiHelper.icon("terminal-2"));
            anotherButton.addActionListener(e -> {
                var s = registry.launchSession(row.projectId(), row.roleId());
                var briefing = generateBriefing(row);
                launcher.launch(row.projectPath(), sessionLabel(row.projectName(), row.roleLabel()),
                        briefing, s.index());
            });

            if (isSelected) {
                selectedFocusAction  = focusButton::doClick;
                selectedLaunchAction = anotherButton::doClick;
            }
            right.add(statusLabel);
            right.add(briefButton);
            right.add(focusButton);
            right.add(anotherButton);

            applyRunningRowExtras(panel, left, statusLabel, session, fullLabel);
        } else {
            var statusLabel = new JLabel("● Active");
            statusLabel.setForeground(UIManager.getColor("Component.accentColor"));

            var toggleButton = new JButton("Set Idle");
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
        addRoleSelectListener(panel, left, row.projectId(), row.roleId());
        return panel;
    }

    private JPanel buildMultiRunningRow(RowData row, List<Session> sessions) {
        var container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        // Header: "Role (N)" + Brief button
        var header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.setOpaque(true);
        var headerSelected = row.projectId().equals(selectedProjectId)
                && row.roleId().equals(selectedRoleId) && selectedSessionId == null;
        if (headerSelected) header.setBackground(UIManager.getColor("List.selectionBackground"));

        var headerLabel = new JLabel(row.roleLabel() + " (" + sessions.size() + ")");
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD));

        var headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerLeft.setOpaque(false);
        headerLeft.add(headerLabel);

        var briefButton = buildBriefButton(row);
        var headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setOpaque(false);
        headerRight.add(briefButton);

        if (headerSelected) selectedBriefAction = briefButton::doClick;

        header.add(headerLeft, BorderLayout.CENTER);
        header.add(headerRight, BorderLayout.EAST);
        addRoleSelectListener(header, headerLeft, row.projectId(), row.roleId());
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, header.getPreferredSize().height));
        container.add(header);

        // Sub-rows: one per running session
        var baseLabel = sessionLabel(row.projectName(), row.roleLabel());
        for (int i = 0; i < sessions.size(); i++) {
            var session = sessions.get(i);
            var isLast  = (i == sessions.size() - 1);

            var sep = new JSeparator();
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, sep.getPreferredSize().height));
            container.add(sep);

            var subRow = new JPanel(new BorderLayout(8, 0));
            subRow.setBorder(new EmptyBorder(6, 28, 6, 12));
            subRow.setOpaque(true);
            var subSelected = session.id().equals(selectedSessionId);
            if (subSelected) subRow.setBackground(UIManager.getColor("List.selectionBackground"));

            var statusLabel = new JLabel("● Running");
            statusLabel.setForeground(UIManager.getColor("Component.accentColor"));

            var subLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            subLeft.setOpaque(false);
            subLeft.add(statusLabel);

            var subRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            subRight.setOpaque(false);

            var fullLabel = fullSessionLabel(row.projectName(), row.roleLabel(), session.index());
            var focusButton = ForemanUiHelper.iconButton("Focus", ForemanUiHelper.icon("focus-2"));
            focusButton.addActionListener(e -> launcher.focus(fullLabel));
            subRight.add(focusButton);

            if (isLast) {
                var anotherButton = ForemanUiHelper.iconButton("+ Another",
                        ForemanUiHelper.icon("terminal-2"));
                anotherButton.addActionListener(e -> {
                    var s = registry.launchSession(row.projectId(), row.roleId());
                    var briefing = generateBriefing(row);
                    launcher.launch(row.projectPath(), baseLabel, briefing, s.index());
                });
                subRight.add(anotherButton);
                if (subSelected) {
                    selectedFocusAction  = focusButton::doClick;
                    selectedLaunchAction = anotherButton::doClick;
                }
            } else {
                if (subSelected) {
                    selectedFocusAction  = focusButton::doClick;
                    selectedLaunchAction = () -> {};
                }
            }

            subRow.add(subLeft, BorderLayout.CENTER);
            subRow.add(subRight, BorderLayout.EAST);

            applyRunningRowExtras(subRow, subLeft, statusLabel, session, fullLabel);

            final var sessionId = session.id();
            var selectListener = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    selectedProjectId = row.projectId();
                    selectedRoleId    = row.roleId();
                    selectedSessionId = sessionId;
                    rebuild();
                }
            };
            subRow.addMouseListener(selectListener);
            subLeft.addMouseListener(selectListener);
            for (var c : subLeft.getComponents()) c.addMouseListener(selectListener);

            subRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, subRow.getPreferredSize().height));
            container.add(subRow);
        }

        var wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(container, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildHumanRow(Project project) {
        var sessions    = registry.getSessionsForRole(project.id(), HUMAN_ROLE_ID);
        var isRunning   = sessions.stream().anyMatch(Session::active);

        var panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));
        panel.setOpaque(true);

        var isSelected = project.id().equals(selectedProjectId)
                && HUMAN_ROLE_ID.equals(selectedRoleId) && selectedSessionId == null;
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
                selectedSessionId = null;
                rebuild();
            }
        };
        panel.addMouseListener(selectListener);
        left.addMouseListener(selectListener);
        for (var c : left.getComponents()) c.addMouseListener(selectListener);

        return panel;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private JButton buildBriefButton(RowData row) {
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
        return briefButton;
    }

    private String generateBriefing(RowData row) {
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
        return briefingService.generate(project, role);
    }

    private void addRoleSelectListener(JPanel panel, JPanel left, String projectId, String roleId) {
        var selectListener = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                selectedProjectId = projectId;
                selectedRoleId    = roleId;
                selectedSessionId = null;
                rebuild();
            }
        };
        panel.addMouseListener(selectListener);
        left.addMouseListener(selectListener);
        for (var c : left.getComponents()) c.addMouseListener(selectListener);
    }

    private void applyRunningRowExtras(JPanel panel, JPanel left, JLabel statusLabel,
                                        Session session, String fullLabel) {
        var tty = launcher.getTty(fullLabel);
        if (tty != null) {
            var tip = "TTY: " + tty;
            panel.setToolTipText(tip);
            left.setToolTipText(tip);
            statusLabel.setToolTipText(tip);
        }

        var popup = new JPopupMenu();
        var markStopped = new JMenuItem("Mark as stopped");
        markStopped.addActionListener(e -> registry.stopSession(session.id()));
        popup.add(markStopped);

        var popupListener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShow(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShow(e); }
            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) popup.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        panel.addMouseListener(popupListener);
        left.addMouseListener(popupListener);
        statusLabel.addMouseListener(popupListener);
    }

    static String fullSessionLabel(String projectName, String roleLabel, int index) {
        var base = sessionLabel(projectName, roleLabel);
        return index > 1 ? base + " #" + index : base;
    }

    static String sessionLabel(String projectName, String roleLabel) {
        return "Foreman · " + projectName + " / " + roleLabel;
    }
}
