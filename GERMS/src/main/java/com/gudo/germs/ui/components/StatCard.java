package com.gudo.germs.ui.components;

import com.gudo.germs.ui.theme.GermsColors;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Summary card: small title, large value, colored accent edge. */
public final class StatCard extends JPanel {
    private final Color accent;
    private JLabel valueLabel;
    private JLabel titleLabel;

    public StatCard(String title, String value, Color accent) {
        this.accent = accent;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 16));
        titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        valueLabel = new JLabel(value);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 28f));
        add(titleLabel);
        add(valueLabel);
        updateUI();
    }

    public void setValue(String value) { valueLabel.setText(value); }

    @Override public void updateUI() {
        super.updateUI();
        if (titleLabel != null) titleLabel.setForeground(GermsColors.SECONDARY_TEXT);
        if (valueLabel != null) valueLabel.setForeground(GermsColors.text());
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(GermsColors.surface());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(GermsColors.border());
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
        g2.setColor(accent);
        g2.fillRoundRect(0, 10, 5, getHeight() - 20, 5, 5);
        g2.dispose();
        super.paintComponent(g);
    }
}
