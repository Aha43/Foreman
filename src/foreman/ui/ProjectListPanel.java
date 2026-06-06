package foreman.ui;

import foreman.domain.Project;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class ProjectListPanel extends JPanel {

    private final DefaultListModel<Project> model = new DefaultListModel<>();
    private final JList<Project> list;

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

        add(new JScrollPane(list), BorderLayout.CENTER);
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
