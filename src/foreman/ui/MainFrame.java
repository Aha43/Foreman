package foreman.ui;

import foreman.app.AppInfo;
import foreman.app.ForemanWorkspaceService;

import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {

    public MainFrame(ForemanWorkspaceService service) {
        super(AppInfo.NAME + " " + AppInfo.VERSION);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 680);
        setLocationRelativeTo(null);

        var workspace = service.getWorkspace();
        var listPanel   = new ProjectListPanel(workspace.projects());
        var detailPanel = new ProjectDetailPanel();

        listPanel.onSelectionChanged(project -> {
            if (project != null) detailPanel.showProject(project);
            else detailPanel.clearProject();
        });

        var selected = listPanel.getSelectedProject();
        if (selected != null) detailPanel.showProject(selected);

        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, detailPanel);
        split.setDividerLocation(240);
        split.setDividerSize(4);

        add(split, BorderLayout.CENTER);
    }
}
