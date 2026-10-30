package com.gudo.germs.ui.incident;

import com.gudo.germs.models.Incident;
import com.gudo.germs.models.IncidentStatus;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.ResponseTeam;
import com.gudo.germs.models.Role;
import com.gudo.germs.models.TimelineEntry;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.services.IncidentService;
import com.gudo.germs.ui.components.StatusBadge;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Main workflow screen: report an incident, publish it, assign a team, step it through response, close it. */
public final class IncidentPanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MMM d, HH:mm").withZone(ZoneId.systemDefault());
    private static final String[] TYPES = {"Flood", "Fire", "Landslide", "Typhoon", "Vehicular accident", "Medical emergency", "Other"};
    private static final String[] SEVERITIES = {"low", "moderate", "high", "critical"};

    private final IncidentService service;
    private final Profile user;
    private final boolean admin;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[] {"ID", "Incident", "Severity", "Status", "Team"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel heading = new JLabel("Select an incident");
    private final JLabel meta = new JLabel(" ");
    private final JTextArea timeline = new JTextArea();
    private final JComboBox<ResponseTeam> teamBox = new JComboBox<>();
    private final JButton assign = new JButton("Assign team");
    private final JButton advance = new JButton("Advance");
    private final JLabel message = new JLabel(" ");

    private List<Incident> rows = List.of();
    private Runnable unsubscribe;

    public IncidentPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 12));
        this.service = ctx.incidents;
        this.user = user;
        this.admin = user.role() == Role.ADMIN;
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildSplit(), BorderLayout.CENTER);
        updateDetail();
    }

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel title = new JLabel("Incidents");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        JButton report = new JButton("+ Report incident");
        report.setBackground(GermsColors.PRIMARY_RED);
        report.setForeground(java.awt.Color.WHITE);
        report.addActionListener(e -> reportDialog());
        p.add(title, BorderLayout.WEST);
        p.add(report, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(p, BorderLayout.NORTH);
        south.add(message, BorderLayout.SOUTH);
        return south;
    }

    private JComponent buildSplit() {
        table.setRowHeight(30);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateDetail(); });
        table.getColumnModel().getColumn(0).setMaxWidth(80);
        DefaultTableCellRenderer colored = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
                super.getTableCellRendererComponent(t, v, sel, foc, r, c);
                if (!sel && r < rows.size()) {
                    Incident i = rows.get(table.convertRowIndexToModel(r));
                    setForeground(c == 2 ? GermsColors.forSeverity(i.severity()) : GermsColors.forIncidentStatus(i.status().key()));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return this;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(colored);
        table.getColumnModel().getColumn(3).setCellRenderer(colored);

        JPanel detail = new JPanel();
        detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
        detail.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 18f));
        meta.setForeground(GermsColors.SECONDARY_TEXT);
        timeline.setEditable(false);
        timeline.setLineWrap(true);
        timeline.setWrapStyleWord(true);
        JScrollPane tlScroll = new JScrollPane(timeline);
        tlScroll.setPreferredSize(new Dimension(320, 220));

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 8));
        actions.setOpaque(false);
        JPanel teamRow = new JPanel(new BorderLayout(8, 0));
        teamRow.setOpaque(false);
        teamRow.add(teamBox, BorderLayout.CENTER);
        teamRow.add(assign, BorderLayout.EAST);
        actions.add(teamRow);
        actions.add(advance);
        assign.addActionListener(e -> doAssign());
        teamBox.addActionListener(e -> {
            Incident current = selected();
            assign.setEnabled(admin && current != null && !current.status().isTerminal()
                    && teamBox.getSelectedItem() != null);
        });
        advance.addActionListener(e -> doAdvance());
        if (!admin) {
            JLabel ro = new JLabel("Only admins can change incident status or assign response teams.");
            ro.setForeground(GermsColors.SECONDARY_TEXT);
            actions.add(ro);
        }
        for (JComponent c : new JComponent[] {heading, meta, tlScroll, actions}) c.setAlignmentX(LEFT_ALIGNMENT);
        detail.add(heading);
        detail.add(Box.createVerticalStrut(4));
        detail.add(meta);
        detail.add(Box.createVerticalStrut(12));
        detail.add(tlScroll);
        detail.add(Box.createVerticalStrut(12));
        detail.add(actions);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(table), detail);
        split.setResizeWeight(0.62);
        split.setBorder(null);
        return split;
    }

    /** Subscribe while on screen (Observer); unsubscribe when removed so logout leaves no stale listeners. */
    @Override public void addNotify() {
        super.addNotify();
        if (unsubscribe == null) unsubscribe = service.bus().subscribe(e -> refresh());
        loadTeams();
        refresh();
    }

    @Override public void removeNotify() {
        if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
        super.removeNotify();
    }

    private void refresh() {
        String keep = selected() == null ? null : selected().id();
        BackgroundTask.run(() -> service.visibleTo(false), list -> {
            rows = list;
            model.setRowCount(0);
            int sel = -1;
            for (int i = 0; i < list.size(); i++) {
                Incident inc = list.get(i);
                model.addRow(new Object[] {inc.reference(), inc.title(), StatusBadge.label(inc.severity()),
                        inc.status().label(), inc.team() == null ? "\u2014" : inc.team()});
                if (inc.id().equals(keep)) sel = i;
            }

            if (sel >= 0) table.setRowSelectionInterval(sel, sel);
            updateDetail();
        }, err -> showMessage("Could not load incidents: " + err.getMessage(), true));
    }

    private void loadTeams() {
        BackgroundTask.run(service::responseTeams, teams -> {
            teamBox.removeAllItems();
            for (ResponseTeam team : teams) teamBox.addItem(team);
            updateDetail();
        }, err -> showMessage("Could not load response teams: " + err.getMessage(), true));
    }

    private Incident selected() {
        int r = table.getSelectedRow();
        return r < 0 || r >= rows.size() ? null : rows.get(table.convertRowIndexToModel(r));
    }

    private void updateDetail() {
        Incident i = selected();
        if (i == null) {
            heading.setText("Select an incident");
            meta.setText(" ");
            timeline.setText("");
            advance.setText("Advance");
            advance.setEnabled(false);
            assign.setEnabled(false);
            teamBox.setEnabled(false);
            return;
        }
        heading.setText(i.reference() + " \u2013 " + i.title());
        meta.setText(i.type() + "  \u00B7  " + i.location() + "  \u00B7  Reported by " + i.reportedBy());
        StringBuilder sb = new StringBuilder();
        for (TimelineEntry t : i.timeline()) {
            sb.append(TIME.format(t.at())).append("  ").append(t.actor()).append('\n').append("    ").append(t.text()).append("\n\n");
        }
        timeline.setText(sb.toString());
        timeline.setCaretPosition(0);
        var next = i.status().next();
        advance.setText(next.map(n -> "Advance to: " + n.label()).orElse("No further steps (" + i.status().label() + ")"));
        advance.setEnabled(next.isPresent() && admin);
        boolean canAssign = admin && !i.status().isTerminal();
        teamBox.setEnabled(canAssign);
        for (int index = 0; index < teamBox.getItemCount(); index++) {
            if (teamBox.getItemAt(index).name().equals(i.team())) {
                teamBox.setSelectedIndex(index);
                assign.setEnabled(canAssign);
                return;
            }
        }
        teamBox.setSelectedItem(null);
        assign.setEnabled(false);
    }

    private void doAdvance() {
        Incident i = selected();
        if (i == null) return;
        String closureSummary = null;
        if (i.status().next().orElse(null) == IncidentStatus.CLOSED) {
            closureSummary = JOptionPane.showInputDialog(this, "Enter the incident closure summary:",
                    "Close incident", JOptionPane.PLAIN_MESSAGE);
            if (closureSummary == null) return;
            if (closureSummary.isBlank()) {
                showMessage("A closure summary is required.", true);
                return;
            }
        }
        String summary = closureSummary;
        BackgroundTask.run(() -> service.advance(i.id(), user.fullName(), summary),
                u -> showMessage(u.reference() + " is now " + u.status().label() + ".", false),
                err -> showMessage(err.getMessage(), true));
    }

    private void doAssign() {
        Incident i = selected();
        if (i == null) return;
        ResponseTeam team = (ResponseTeam) teamBox.getSelectedItem();
        if (team == null) return;
        BackgroundTask.run(() -> service.assignTeam(i.id(), team),
                u -> showMessage(team.name() + " assigned to " + u.reference() + ".", false),
                err -> showMessage(err.getMessage(), true));
    }

    private void reportDialog() {
        JTextField title = new JTextField(22);
        JTextField location = new JTextField(22);
        JComboBox<String> type = new JComboBox<>(TYPES);
        JComboBox<String> severity = new JComboBox<>(SEVERITIES);
        severity.setSelectedItem("moderate");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Title")); form.add(title);
        form.add(new JLabel("Type")); form.add(type);
        form.add(new JLabel("Location")); form.add(location);
        form.add(new JLabel("Severity")); form.add(severity);
        if (JOptionPane.showConfirmDialog(this, form, "Report an incident",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (admin) {
            BackgroundTask.run(() -> service.report(title.getText(), (String) type.getSelectedItem(),
                            location.getText(), (String) severity.getSelectedItem(), user.fullName()),
                    i -> showMessage(i.reference() + " created as Draft. An admin must publish it.", false),
                    err -> showMessage(err.getMessage(), true));
        } else {
            BackgroundTask.run(() -> service.submitGuestReport(title.getText(), (String) type.getSelectedItem(),
                            location.getText(), (String) severity.getSelectedItem(), user.fullName()),
                    i -> showMessage(i.reference() + " submitted for dispatcher verification.", false),
                    err -> showMessage(err.getMessage(), true));
        }
    }

    private void showMessage(String text, boolean error) {
        message.setText(text);
        message.setForeground(error ? GermsColors.CRITICAL : GermsColors.SUCCESS);
    }
}
