package com.gudo.germs.ui.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;
import java.util.function.Supplier;

/** Panel whose background is re-resolved on every theme switch (updateUI is called by FlatLaf.updateUI). */
public class SurfacePanel extends JPanel {
    private Supplier<Color> background;

    public SurfacePanel(LayoutManager layout, Supplier<Color> background) {
        super(layout);
        this.background = background;
        updateUI();
    }

    @Override public void updateUI() {
        super.updateUI();
        if (background != null) setBackground(background.get());
    }
}
