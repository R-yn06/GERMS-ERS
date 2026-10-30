package com.gudo.germs.ui.admin;

import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.Profile;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.ui.components.StatCard;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/** Dashboard driven by the same IncidentService as the Incidents screen; refreshes via the event bus. */
public final class DashboardPanel extends JPanel {
    private final IncidentService service = IncidentService.get();
    private final boolean guest;
    private final StatCard visible = new StatCard("Incidents visible to you", "\u2026", GermsColors.INFO);
    private final StatCard active = new StatCard("Active incidents", "\u2026", GermsColors.CRITICAL);
    private final StatCard critical = new StatCard("Critical & active", "\u2026", GermsColors.WARNING);
    private final StatCard closed = new StatCard("Closed", "\u2026", GermsColors.SUCCESS);
    private Runnable unsubscribe;

    public DashboardPanel(AppContext ctx, Profile user) {
        this.guest = user.role().name().equals("GUEST");
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("Welcome, " + user.fullName());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(16));

        JPanel row = new JPanel(new GridLayout(1, 4, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 100));
        row.add(visible);
        row.add(active);
        row.add(critical);
        row.add(closed);
        add(row);
        add(Box.createVerticalStrut(16));

        JLabel note = new JLabel("<html>Counts update <b>instantly</b> when an incident is reported or changes status "
                + "(in-process event bus). Data is a prepared in-memory sample set; Supabase persistence is a later phase.</html>");
        note.setForeground(GermsColors.SECONDARY_TEXT);
        note.setAlignmentX(LEFT_ALIGNMENT);
        add(note);
        add(Box.createVerticalGlue());
    }

    @Override public void addNotify() {
        super.addNotify();
        if (unsubscribe == null) unsubscribe = service.bus().subscribe(e -> load());
        load();
    }

    @Override public void removeNotify() {
        if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
        super.removeNotify();
    }

    private void load() {
        BackgroundTask.run(() -> service.visibleTo(guest), (List<Incident> list) -> {
            long act = list.stream().filter(i -> i.status().isActive()).count();
            long crit = list.stream().filter(i -> i.status().isActive() && "critical".equals(i.severity())).count();
            long done = list.stream().filter(i -> i.status() == IncidentStatus.CLOSED).count();
            visible.setValue(String.valueOf(list.size()));
            active.setValue(String.valueOf(act));
            critical.setValue(String.valueOf(crit));
            closed.setValue(String.valueOf(done));
        }, err -> {
            visible.setValue("\u2014");
            active.setValue("\u2014");
            critical.setValue("\u2014");
            closed.setValue("\u2014");
        });
    }
}
