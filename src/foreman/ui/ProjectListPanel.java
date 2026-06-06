package foreman.ui;

import foreman.domain.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class ProjectListPanel extends JPanel {

    private final DefaultListModel<Project> model = new DefaultListModel<>();
    private final JList<Project> list;
    private Consumer<Project> removeListener;

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

        var contextMenu   = new JPopupMenu();
        var removeItem    = new JMenuItem("Remove project…");
        contextMenu.add(removeItem);
        removeItem.addActionListener(e -> {
            var project = list.getSelectedValue();
            if (project != null && removeListener != null) removeListener.accept(project);
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { maybeShowMenu(e); }
            @Override public void mouseReleased(MouseEvent e) { maybeShowMenu(e); }
            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                var index = list.locationToIndex(e.getPoint());
                if (index >= 0) list.setSelectedIndex(index);
                if (list.getSelectedValue() != null) contextMenu.show(list, e.getX(), e.getY());
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    public void onRemove(Consumer<Project> listener) {
        this.removeListener = listener;
    }

    public void addProject(Project project) {
        model.addElement(project);
        list.setSelectedValue(project, true);
    }

    public void removeProject(Project project) {
        var nextIndex = list.getSelectedIndex();
        model.removeElement(project);
        if (!model.isEmpty()) {
            list.setSelectedIndex(Math.min(nextIndex, model.size() - 1));
        }
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
