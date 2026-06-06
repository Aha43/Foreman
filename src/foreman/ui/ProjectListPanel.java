package foreman.ui;

import foreman.domain.Project;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class ProjectListPanel extends JPanel {

    private final DefaultListModel<Project> model = new DefaultListModel<>();
    private final JList<Project> list;
    private Runnable registerListener;

    public ProjectListPanel(List<Project> projects) {
        super(new BorderLayout());
        projects.forEach(model::addElement);

        list = new JList<>(model) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (model.isEmpty()) {
                    var fm = g.getFontMetrics();
                    var text = "No projects yet.";
                    var x = (getWidth() - fm.stringWidth(text)) / 2;
                    var y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                    g.setColor(UIManager.getColor("Label.disabledForeground"));
                    g.drawString(text, x, y);
                }
            }
        };

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(((Project) value).name());
                return this;
            }
        });

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }

        var registerButton = new JButton("Register Project");
        registerButton.addActionListener(e -> { if (registerListener != null) registerListener.run(); });

        var toolbar = new JPanel(new BorderLayout());
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        toolbar.add(registerButton, BorderLayout.CENTER);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public void onRegister(Runnable listener) {
        this.registerListener = listener;
    }

    public void addProject(Project project) {
        model.addElement(project);
        list.setSelectedValue(project, true);
    }

    public void onSelectionChanged(Consumer<Project> handler) {
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                handler.accept(list.getSelectedValue());
            }
        });
    }

    public Project getSelectedProject() {
        return list.getSelectedValue();
    }
}
