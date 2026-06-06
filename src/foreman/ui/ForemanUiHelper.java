package foreman.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import java.awt.*;

public final class ForemanUiHelper {

    private static final String PROP_DENSEABLE = "foreman.denseable";
    private static final String PROP_LABEL     = "foreman.label";

    private static boolean dense = false;

    private ForemanUiHelper() {}

    public static void init(boolean dense) {
        ForemanUiHelper.dense = dense;
    }

    /** Dense-aware button: icon+text normally, icon-only when dense mode is on. */
    public static JButton iconButton(String label, Icon icon) {
        var btn = new JButton(dense ? "" : label, icon);
        btn.setToolTipText(label);
        btn.putClientProperty(PROP_DENSEABLE, Boolean.TRUE);
        btn.putClientProperty(PROP_LABEL, label);
        return btn;
    }

    /** Always icon-only regardless of dense mode — for compact inline contexts. */
    public static JButton iconOnlyButton(String label, Icon icon) {
        var btn = new JButton("", icon);
        btn.setToolTipText(label);
        return btn;
    }

    /** Load an SVG icon from the classpath at /icons/<name>.svg, sized 16×16. */
    public static Icon icon(String name) {
        var url = ForemanUiHelper.class.getResource("/icons/" + name + ".svg");
        if (url == null) return null;
        return new FlatSVGIcon(url).derive(16, 16);
    }

    /** Toggle dense mode on all open windows. */
    public static void applyDense(boolean dense) {
        ForemanUiHelper.dense = dense;
        for (var w : Window.getWindows()) applyDenseToContainer(w, dense);
    }

    private static void applyDenseToContainer(Container c, boolean dense) {
        for (var comp : c.getComponents()) {
            if (comp instanceof AbstractButton btn
                    && Boolean.TRUE.equals(btn.getClientProperty(PROP_DENSEABLE))) {
                btn.setText(dense ? "" : (String) btn.getClientProperty(PROP_LABEL));
            }
            if (comp instanceof Container sub) applyDenseToContainer(sub, dense);
        }
    }
}
