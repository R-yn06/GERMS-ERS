package com.gudo.germs.ui.components;

import com.gudo.germs.ui.theme.GermsColors;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small colored pill used for severity, incident status, and role labels. */
public final class StatusBadge extends JLabel {
    private final Color fill;

    public StatusBadge(String text, Color fill) {
        super(text, SwingConstants.CENTER);
        this.fill = fill;
        setForeground(Color.WHITE);
        setFont(getFont().deriveFont(Font.BOLD, 11f));
        setOpaque(false);
    }

    public static StatusBadge severity(String severity) {
        return new StatusBadge(label(severity), GermsColors.forSeverity(severity));
    }

    public static StatusBadge incidentStatus(String status) {
        return new StatusBadge(label(status), GermsColors.forIncidentStatus(status));
    }

    public static String label(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String s = raw.replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width + 18, d.height + 6);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(fill);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}
