package foreman.ui;

import foreman.app.AppInfo;
import foreman.app.ForemanSettings;
import foreman.app.ForemanSettingsService;
import foreman.app.ForemanWorkspaceService;
import foreman.app.ProjectRegistrationService;
import foreman.app.RoleDiscoveryService;
import foreman.app.SessionRegistry;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame(ForemanWorkspaceService service, ForemanSettingsService settingsService) {
        super(AppInfo.NAME + " " + AppInfo.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        var registrationService = new ProjectRegistrationService(new RoleDiscoveryService());
        var sessionRegistry     = new SessionRegistry();

        var workspace    = service.getWorkspace();
        var listPanel    = new ProjectListPanel(workspace.projects());
        var detailPanel  = new ProjectDetailPanel();
        var sessionPanel = new SessionPanel(service, sessionRegistry);

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

        var registerBtn = new JButton("Register Project");
        registerBtn.addActionListener(e -> {
            var initialDir = java.nio.file.Path.of(settingsService.get().defaultProjectDir());
            RegisterProjectDialog.show(this, initialDir).ifPresent(result -> {
                var project = registrationService.register(result.path(), result.name(), service);
                listPanel.addProject(project);
                detailPanel.showProject(project);
                sessionPanel.reload();
                var parent = result.path().getParent();
                if (parent != null) {
                    settingsService.update(new ForemanSettings(parent.toAbsolutePath().toString()));
                }
            });
        });

        var settingsBtn = new JButton("Settings");
        settingsBtn.addActionListener(e -> SettingsDialog.show(this, settingsService));

        var exitBtn = new JButton("Exit");
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
    }
}
