package foreman.ui;

import foreman.app.AppInfo;
import foreman.app.ForemanSettings;
import foreman.app.ForemanSettingsService;
import foreman.app.ForemanWorkspaceService;
import foreman.app.ProjectRegistrationService;
import foreman.app.RoleDiscoveryService;
import foreman.app.SessionRegistry;
import foreman.app.TerminalLauncher;

import javax.swing.*;
import java.awt.*;
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
        var listPanel    = new ProjectListPanel(workspace.projects());
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
                var project = registrationService.register(result.path(), result.name(), service);
                listPanel.addProject(project);
                detailPanel.showProject(project);
                sessionPanel.reload();
                var parent = result.path().getParent();
                if (parent != null) {
                    settingsService.update(new ForemanSettings(parent.toAbsolutePath().toString(), settingsService.get().isDense()));
                }
            });
        });

        var settingsBtn = ForemanUiHelper.iconButton("Settings", ForemanUiHelper.icon("settings"));
        settingsBtn.addActionListener(e -> SettingsDialog.show(this, settingsService));

        var exitBtn = ForemanUiHelper.iconButton("Exit", ForemanUiHelper.icon("logout"));
        exitBtn.addActionListener(e -> System.exit(0));

        toolbar.add(registerBtn);
        toolbar.add(settingsBtn);
        toolbar.add(Box.createHorizontalGlue());
        toolbar.add(exitBtn);

        var tabs = new JTabbedPane();
        tabs.addTab("Project", detailPanel);
        tabs.addTab("Sessions", sessionPanel);

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
    }
}
