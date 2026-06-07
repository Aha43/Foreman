package foreman.ui;

import foreman.app.AppInfo;
import foreman.app.ForemanSettingsService;
import foreman.app.ForemanWorkspaceService;
import foreman.app.ProjectRegistrationService;
import foreman.app.RoleDiscoveryService;
import foreman.app.SessionRegistry;
import foreman.app.TerminalLauncher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {

    private final TerminalLauncher launcher;

    public MainFrame(ForemanWorkspaceService service, ForemanSettingsService settingsService,
                     TerminalLauncher launcher) {
        super(AppInfo.NAME + " " + AppInfo.VERSION);
        this.launcher = launcher;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        var registrationService = new ProjectRegistrationService(new RoleDiscoveryService());
        var sessionRegistry     = new SessionRegistry();

        var workspace    = service.getWorkspace();
        var listPanel    = new ProjectListPanel(workspace.projects(), sessionRegistry);
        var detailPanel  = new ProjectDetailPanel();
        var sessionPanel = new SessionPanel(service, sessionRegistry, launcher);

        listPanel.onSelectionChanged(project -> {
            if (project != null) detailPanel.showProject(project);
            else detailPanel.clearProject();
        });

        listPanel.onRemove(project -> {
            var answer = JOptionPane.showConfirmDialog(
                    this,
                    "Remove \"" + project.name() + "\" from workspace?",
                    "Remove Project",
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
            service.removeProject(project.id());
            sessionRegistry.dropProject(project.id());
            listPanel.removeProject(project);
            detailPanel.clearProject();
            sessionPanel.reload();
        });

        var selected = listPanel.getSelectedProject();
        if (selected != null) detailPanel.showProject(selected);

        // toolbar
        var toolbar = new JToolBar();
        toolbar.setFloatable(false);

        var registerBtn = ForemanUiHelper.iconButton("Register Project", ForemanUiHelper.icon("folder-plus"));
        registerBtn.addActionListener(e -> {
            var initialDir = java.nio.file.Path.of(settingsService.get().defaultProjectDir());
            RegisterProjectDialog.show(this, initialDir).ifPresent(result -> {
                var project = registrationService.register(result.path(), result.name(), result.workflowPath(), service);
                listPanel.addProject(project);
                detailPanel.showProject(project);
                sessionPanel.reload();
                var assignments = project.team().assignments();
                if (assignments.isEmpty()) {
                    listPanel.showFeedback("Registered " + project.name()
                            + " — no roles discovered (docs/roles/ not found)");
                } else {
                    var labels = assignments.stream()
                            .map(a -> a.label())
                            .collect(java.util.stream.Collectors.joining(", "));
                    listPanel.showFeedback("Registered " + project.name()
                            + " — found " + assignments.size()
                            + (assignments.size() == 1 ? " role: " : " roles: ") + labels);
                }
                var parent = result.path().getParent();
                if (parent != null) {
                    settingsService.update(settingsService.get()
                            .withDefaultProjectDir(parent.toAbsolutePath().toString()));
                }
            });
        });

        var helpPanel = new HelpPanel(settingsService, true);

        var helpBtn = ForemanUiHelper.iconButton("Help", ForemanUiHelper.icon("help-circle"));

        var settingsBtn = ForemanUiHelper.iconButton("Settings", ForemanUiHelper.icon("settings"));
        settingsBtn.addActionListener(e -> SettingsDialog.show(this, settingsService));

        var shortcutsBtn = ForemanUiHelper.iconButton("Shortcuts", ForemanUiHelper.icon("keyboard"));
        shortcutsBtn.addActionListener(e -> ShortcutsDialog.show(this));

        var aboutBtn = ForemanUiHelper.iconButton("About", ForemanUiHelper.icon("info-circle"));
        aboutBtn.addActionListener(e -> AboutDialog.show(this));

        var exitBtn = ForemanUiHelper.iconButton("Exit", ForemanUiHelper.icon("logout"));
        exitBtn.addActionListener(e -> System.exit(0));

        toolbar.add(registerBtn);
        toolbar.add(settingsBtn);
        toolbar.add(shortcutsBtn);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(helpBtn);
        toolbar.add(aboutBtn);
        toolbar.add(exitBtn);

        var tabs = new JTabbedPane();
        tabs.addTab("Project", detailPanel);
        tabs.addTab("Sessions", sessionPanel);
        tabs.addTab("Help", helpPanel);
        helpBtn.addActionListener(e -> tabs.setSelectedComponent(helpPanel));

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, tabs);
        split.setDividerLocation(240);
        split.setDividerSize(4);

        add(toolbar, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                sessionPanel.reload();
            }
        });

        var mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var im   = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var am   = getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_R,     mask), "shortcut_register");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, mask), "shortcut_settings");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q,     mask), "shortcut_quit");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_SLASH, mask), "shortcut_keyboard");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W,     mask), "shortcut_close");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F1,    0),    "shortcut_help");
        am.put("shortcut_register", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { registerBtn.doClick(); }
        });
        am.put("shortcut_settings", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { settingsBtn.doClick(); }
        });
        am.put("shortcut_quit", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { exitBtn.doClick(); }
        });
        am.put("shortcut_keyboard", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { ShortcutsDialog.show(MainFrame.this); }
        });
        am.put("shortcut_close", new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(MainFrame.this, WindowEvent.WINDOW_CLOSING));
            }
        });
        am.put("shortcut_help", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { helpBtn.doClick(); }
        });
        am.put("shortcut_about", new AbstractAction() {
            public void actionPerformed(ActionEvent e) { AboutDialog.show(MainFrame.this); }
        });
    }
}
