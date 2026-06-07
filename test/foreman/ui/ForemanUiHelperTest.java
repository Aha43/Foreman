package foreman.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

class ForemanUiHelperTest {

    @AfterEach
    void resetDense() {
        ForemanUiHelper.init(false);
    }

    @Test
    void iconButtonShowsLabelWhenNotDense() {
        ForemanUiHelper.init(false);
        var btn = ForemanUiHelper.iconButton("Launch", null);
        assertEquals("Launch", btn.getText());
        assertEquals("Launch", btn.getToolTipText());
    }

    @Test
    void iconButtonHidesLabelWhenDense() {
        ForemanUiHelper.init(true);
        var btn = ForemanUiHelper.iconButton("Launch", null);
        assertEquals("", btn.getText());
        assertEquals("Launch", btn.getToolTipText());
    }

    @Test
    void iconOnlyButtonAlwaysHidesLabel() {
        ForemanUiHelper.init(false);
        var btn = ForemanUiHelper.iconOnlyButton("Focus", null);
        assertEquals("", btn.getText());
        assertEquals("Focus", btn.getToolTipText());
    }

    @Test
    void applyDenseHidesTextOnDenseableButtons() {
        ForemanUiHelper.init(false);
        var btn = ForemanUiHelper.iconButton("Settings", null);
        var frame = new JFrame();
        frame.add(btn);
        frame.pack();

        ForemanUiHelper.applyDense(true);
        assertEquals("", btn.getText());

        ForemanUiHelper.applyDense(false);
        assertEquals("Settings", btn.getText());

        frame.dispose();
    }

    @Test
    void isDenseNullSafe() {
        var s = new foreman.app.ForemanSettings("/path", null);
        assertFalse(s.isDense());
    }
}
