package foreman.ui;

import foreman.app.AppInfo;
import foreman.app.ForemanWorkspaceService;
import foreman.app.ProjectRegistrationService;
import foreman.app.RoleDiscoveryService;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame(ForemanWorkspaceService service) {
        super(AppInfo.NAME + " " + AppInfo.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        var registrationService = new ProjectRegistrationService(new RoleDiscoveryService());

        var workspace   = service.getWorkspace();
        var listPanel   = new ProjectListPanel(workspace.projects());
        var detailPanel = new ProjectDetailPanel();

        listPanel.onSelectionChanged(project -> {
            if (project != null) detailPanel.showProject(project);
            else detailPanel.clearProject();
        });

        listPanel.onRegister(() -> {
            var owner = (Frame) SwingUtilities.getWindowAncestor(listPanel);
            RegisterProjectDialog.show(owner).ifPresent(result -> {
                var project = registrationService.register(result.path(), result.name(), service);
                listPanel.addProject(project);
                detailPanel.showProject(project);
            });
        });

        var selected = listPanel.getSelectedProject();
        if (selected != null) detailPanel.showProject(selected);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, detailPanel);
        split.setDividerLocation(240);
        split.setDividerSize(4);

        add(split, BorderLayout.CENTER);
    }
}
