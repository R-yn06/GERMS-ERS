package com.gudo.germs.ui.incident;

import com.fasterxml.jackson.databind.JsonNode;
import com.gudo.germs.config.AppContext;
import com.gudo.germs.models.Incident;
import com.gudo.germs.models.Profile;
import com.gudo.germs.models.Role;
import com.gudo.germs.ui.components.StatusBadge;
import com.gudo.germs.ui.theme.GermsColors;
import com.gudo.germs.utils.BackgroundTask;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


public final class EmergencyCommunicationPanel extends JPanel {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("EEEE, MMM d");
    private static final int POLL_MS = 3000;
    private static final int SLIDE_MS = 240;
    private static final int AUTO_COLLAPSE_WIDTH = 900;
    private static final int MAX_BUBBLE_TEXT = 520;

    /** Fixed dark palette: this screen is a dark "operations console" in both app themes. */
    private static final class Pal {
        static final Color BG_TOP = new Color(0x0A101C);
        static final Color BG_BOTTOM = new Color(0x131C2E);
        static final Color POPUP = new Color(0x141C2C);
        static final Color TEXT = new Color(0xEEF2F9);
        static final Color DIM = new Color(0x93A4BF);
        static final Color BLUE = new Color(0x4A9BE8);
        static final Color AMBER = new Color(0xF0B429);
        static final Color RED = new Color(0xF2545B);
        static final Color GREEN = new Color(0x3DD68C);
        static final Color BORDER = new Color(255, 255, 255, 36);
    }

    private enum Kind { REFRESH, SEND, PANEL, MEGAPHONE, WARNING, MESSAGE }

    private final AppContext ctx;
    private final Profile user;

    // channel picker (popup)
    private final DefaultListModel<Incident> incidentModel = new DefaultListModel<>();
    private final JList<Incident> incidentList = new JList<>(incidentModel);
    private final CardLayout listCards = new CardLayout();
    private final JPanel listHolder = new JPanel(listCards);
    private final JLabel countLabel = new JLabel(" ");
    private final JPopupMenu channelPopup = new JPopupMenu();
    private final JPanel popupPanel = new JPanel(new BorderLayout());
    private final GlassButton channelButton = new GlassButton();

    // header
    private final JLabel liveLabel = new JLabel();
    private final IconButton refreshButton = new IconButton(Kind.REFRESH, Pal.TEXT, false, 38, "Refresh");
    private final IconButton panelToggle = new IconButton(Kind.PANEL, Pal.TEXT, false, 38, "Message types");

    // chat
    private final JLabel channelTitle = new JLabel("No channel selected");
    private final JPanel channelMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final FeedPanel feed = new FeedPanel();
    private final JScrollPane feedScroll = new JScrollPane(feed);

    // composer
    private final GlassPanel composer = new GlassPanel(new BorderLayout(8, 0), 22, 30, 16, 60);
    private final PlaceholderArea input = new PlaceholderArea(2, 20);
    private final IconButton sendBtn = new IconButton(Kind.SEND, Pal.BLUE, true, 40, "Send (Enter)");
    private final GlassButton modeTag = new GlassButton();
    private final JPanel modeTagWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    // right sidebar
    private final ModeOption[] options = new ModeOption[3];
    private SlideContainer slide;
    private final Timer slideTimer = new Timer(12, e -> stepSlide());
    private double slideFrom;
    private double slideTo;
    private long slideStart;
    private boolean sidebarOpen = true;
    private boolean wasNarrow;

    // state
    private final Timer pollTimer;
    private final Timer resizeDebounce;
    private Runnable unsubscribe;
    private int tick;
    private boolean updatingList;
    private boolean forceScroll;
    private boolean channelSelected;
    private boolean sending;
    private String mode = "regular";
    private String activeId;
    private String shownChannelId;
    private int shownSignature;
    private JsonNode lastRows;
    private int lastBuildWidth;

    public EmergencyCommunicationPanel(AppContext ctx, Profile user) {
        super(new BorderLayout(0, 16));
        this.ctx = ctx;
        this.user = user;
        setOpaque(true);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        pollTimer = new Timer(POLL_MS, e -> {
            loadMessages();
            if (++tick % 5 == 0) loadIncidents();
        });
        pollTimer.setCoalesce(true);
        resizeDebounce = new Timer(140, e -> onFeedResized());
        resizeDebounce.setRepeats(false);

        buildChannelPopup();
        add(buildHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(buildChatColumn(), BorderLayout.CENTER);
        slide = new SlideContainer(buildOptionsSidebar());
        body.add(slide, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);

        feedScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { resizeDebounce.restart(); }
        });
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                boolean narrow = getWidth() < AUTO_COLLAPSE_WIDTH;
                if (narrow != wasNarrow) {
                    wasNarrow = narrow;
                    if (narrow && sidebarOpen) setSidebarOpen(false, true);
                }
            }
        });

        setSidebarOpen(true, false);
        setMode("regular");
        setLive(true);
        refreshSendState();
        showPlaceholder("Select an incident channel", "Use the channel button at the top right to choose an active incident.");
    }

    // ------------------------------------------------------------------ painting

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        int w = getWidth(), h = Math.max(1, getHeight());
        g2.setPaint(new GradientPaint(0, 0, Pal.BG_TOP, 0, h, Pal.BG_BOTTOM));
        g2.fillRect(0, 0, w, h);
        glow(g2, w, h, w * 0.88f, h * 0.02f, w * 0.55f, new Color(200, 16, 46, 54));
        glow(g2, w, h, w * 0.06f, h * 0.98f, w * 0.50f, new Color(36, 116, 199, 46));
        g2.dispose();
    }

    private static void glow(Graphics2D g2, int w, int h, float cx, float cy, float radius, Color c) {
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), Math.max(1f, radius), new float[] {0f, 1f},
                new Color[] {c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g2.fillRect(0, 0, w, h);
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private static void glass(Graphics2D g2, int x, int y, int w, int h, int arc,
                              Color top, Color bottom, Color border, float borderWidth) {
        g2.setPaint(new GradientPaint(x, y, top, x, y + Math.max(1, h), bottom));
        g2.fillRoundRect(x, y, w, h, arc, arc);
        if (border != null) {
            g2.setStroke(new BasicStroke(borderWidth));
            g2.setColor(border);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
        }
    }

    private static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Color accentFor(String type) {
        return switch (type == null ? "" : type) {
            case "urgent" -> Pal.RED;
            case "announcement" -> Pal.AMBER;
            default -> Pal.BLUE;
        };
    }

    // -------------------------------------------------------------------- layout

    private void buildChannelPopup() {
        incidentList.setCellRenderer(new IncidentCell());
        incidentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        incidentList.setOpaque(false);
        incidentList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingList) applySelection();
        });
        incidentList.addMouseListener(new MouseAdapter() {
            @Override public void mouseReleased(MouseEvent e) { channelPopup.setVisible(false); }
        });

        JScrollPane scroll = new JScrollPane(incidentList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JLabel empty = new JLabel("<html><div style='text-align:center'><b>No active incidents</b><br>"
                + "Channels appear once an incident is published.</div></html>", SwingConstants.CENTER);
        empty.setForeground(Pal.DIM);
        listHolder.setOpaque(false);
        listHolder.add(scroll, "list");
        listHolder.add(empty, "empty");
        listCards.show(listHolder, "empty");

        JLabel head = new JLabel("ACTIVE INCIDENT CHANNELS");
        head.setFont(head.getFont().deriveFont(Font.BOLD, 11f));
        head.setForeground(Pal.DIM);
        countLabel.setForeground(Pal.DIM);
        countLabel.setFont(countLabel.getFont().deriveFont(11f));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
        top.add(head, BorderLayout.WEST);
        top.add(countLabel, BorderLayout.EAST);

        popupPanel.setBackground(Pal.POPUP);
        popupPanel.add(top, BorderLayout.NORTH);
        popupPanel.add(listHolder, BorderLayout.CENTER);
        popupPanel.setPreferredSize(new Dimension(340, 140));
        channelPopup.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 50)));
        channelPopup.setBackground(Pal.POPUP);
        channelPopup.add(popupPanel);
    }

    private JComponent buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Emergency Communication");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(LEFT_ALIGNMENT);
        liveLabel.setFont(liveLabel.getFont().deriveFont(Font.BOLD, 12f));
        liveLabel.setToolTipText("Messages refresh automatically every " + (POLL_MS / 1000) + " seconds");
        liveLabel.setAlignmentX(LEFT_ALIGNMENT);
        left.add(title);
        left.add(Box.createVerticalStrut(3));
        left.add(liveLabel);

        channelButton.setText("Select channel  \u25BE");
        channelButton.addActionListener(e -> {
            if (channelPopup.isVisible()) { channelPopup.setVisible(false); return; }
            channelPopup.show(channelButton, channelButton.getWidth() - popupPanel.getPreferredSize().width,
                    channelButton.getHeight() + 6);
        });
        refreshButton.addActionListener(e -> refreshNow());
        panelToggle.addActionListener(e -> setSidebarOpen(!sidebarOpen, true));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(channelButton);
        right.add(refreshButton);
        right.add(panelToggle);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildChatColumn() {
        JPanel col = new JPanel(new BorderLayout(0, 12));
        col.setOpaque(false);

        GlassPanel card = new GlassPanel(new BorderLayout(), 22, 26, 12, 54);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));
        channelTitle.setFont(channelTitle.getFont().deriveFont(Font.BOLD, 16f));
        channelTitle.setForeground(Pal.TEXT);
        channelMeta.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        channelTitle.setAlignmentX(LEFT_ALIGNMENT);
        channelMeta.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(channelTitle);
        titles.add(Box.createVerticalStrut(6));
        titles.add(channelMeta);
        head.add(titles, BorderLayout.CENTER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(head, BorderLayout.CENTER);
        top.add(new Divider(), BorderLayout.SOUTH);

        feedScroll.setBorder(null);
        feedScroll.setOpaque(false);
        feedScroll.getViewport().setOpaque(false);
        feedScroll.getViewport().setScrollMode(JViewport.SIMPLE_SCROLL_MODE); // no blit artifacts on translucent content
        feedScroll.getVerticalScrollBar().setUnitIncrement(16);
        feedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        card.add(top, BorderLayout.NORTH);
        card.add(feedScroll, BorderLayout.CENTER);

        col.add(card, BorderLayout.CENTER);
        col.add(buildComposer(), BorderLayout.SOUTH);
        return col;
    }

    private JComponent buildComposer() {
        composer.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 10));

        modeTag.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
        modeTag.setFont(modeTag.getFont().deriveFont(Font.BOLD, 10f));
        modeTag.setToolTipText("Click to switch back to a normal message");
        modeTag.addActionListener(e -> setMode("regular"));
        modeTagWrap.setOpaque(false);
        modeTagWrap.add(modeTag);

        input.setPlaceholder("Write a message\u2026   (Enter to send, Shift+Enter for a new line)");
        input.setLineWrap(true);
        input.setWrapStyleWord(true);
        input.setOpaque(false);
        input.setForeground(Pal.TEXT);
        input.setCaretColor(Pal.TEXT);
        input.setDisabledTextColor(Pal.DIM);
        input.setSelectionColor(alpha(Pal.BLUE, 120));
        input.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 8));
        input.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "send");
        input.getInputMap().put(KeyStroke.getKeyStroke("shift ENTER"), "insert-break");
        input.getActionMap().put("send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { sendMessage(); }
        });
        input.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshSendState(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshSendState(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshSendState(); }
        });
        input.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { composer.setFocused(true, accentFor(mode)); }
            @Override public void focusLost(FocusEvent e) { composer.setFocused(false, accentFor(mode)); }
        });

        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setBorder(null);
        inputScroll.setOpaque(false);
        inputScroll.getViewport().setOpaque(false);
        inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inputScroll.setPreferredSize(new Dimension(100, 54));

        sendBtn.addActionListener(e -> sendMessage());

        JPanel center = new JPanel(new BorderLayout(0, 4));
        center.setOpaque(false);
        center.add(modeTagWrap, BorderLayout.NORTH);
        center.add(inputScroll, BorderLayout.CENTER);

        JPanel east = new JPanel(new BorderLayout());
        east.setOpaque(false);
        east.add(sendBtn, BorderLayout.SOUTH);

        composer.add(center, BorderLayout.CENTER);
        composer.add(east, BorderLayout.EAST);
        return composer;
    }

    private JComponent buildOptionsSidebar() {
        GlassPanel card = new GlassPanel(new BorderLayout(), 22, 26, 12, 54);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(BorderFactory.createEmptyBorder(16, 14, 16, 14));

        JLabel head = new JLabel("MESSAGE TYPE");
        head.setFont(head.getFont().deriveFont(Font.BOLD, 11f));
        head.setForeground(Pal.DIM);
        head.setAlignmentX(LEFT_ALIGNMENT);
        JLabel hint = new JLabel("Choose how your next message is sent.");
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setForeground(Pal.DIM);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        col.add(head);
        col.add(Box.createVerticalStrut(2));
        col.add(hint);
        col.add(Box.createVerticalStrut(14));

        options[0] = new ModeOption("regular", Kind.MESSAGE, "Transfer Message", "Standard message", Pal.BLUE);
        options[1] = new ModeOption("announcement", Kind.MEGAPHONE, "Announcement", "Broadcast to everyone", Pal.AMBER);
        options[2] = new ModeOption("urgent", Kind.WARNING, "Urgent", "High-priority alert", Pal.RED);
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < options.length; i++) {
            ModeOption o = options[i];
            o.addActionListener(e -> setMode(o.key));
            group.add(o);
            col.add(o);
            if (i < options.length - 1) col.add(Box.createVerticalStrut(10));
        }
        card.add(col, BorderLayout.CENTER);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        wrapper.add(card, BorderLayout.NORTH); // upper-right, natural height
        return wrapper;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override public void addNotify() {
        super.addNotify();
        if (!pollTimer.isRunning()) pollTimer.start();
        if (unsubscribe == null) unsubscribe = ctx.incidents.bus().subscribe(e -> loadIncidents());
        loadIncidents();
    }

    @Override public void removeNotify() {
        pollTimer.stop();
        channelPopup.setVisible(false);
        if (unsubscribe != null) { unsubscribe.run(); unsubscribe = null; }
        super.removeNotify();
    }

    // ----------------------------------------------------------------------- data

    private void loadIncidents() {
        BackgroundTask.run(() -> ctx.incidents.visibleTo(false), all -> {
            setLive(true);
            Incident previous = incidentList.getSelectedValue();
            String keepId = previous == null ? null : previous.id();
            List<Incident> active = all.stream().filter(i -> i.status().isActive()).toList();

            updatingList = true;
            try {
                incidentModel.clear();
                for (Incident i : active) incidentModel.addElement(i);
                int idx = -1;
                for (int i = 0; i < active.size(); i++) {
                    if (active.get(i).id().equals(keepId)) { idx = i; break; }
                }
                if (idx < 0 && keepId == null && !active.isEmpty()) idx = 0;
                if (idx >= 0) incidentList.setSelectedIndex(idx); else incidentList.clearSelection();
            } finally {
                updatingList = false;
            }

            countLabel.setText(active.size() + (active.size() == 1 ? " channel" : " channels"));
            listCards.show(listHolder, active.isEmpty() ? "empty" : "list");
            popupPanel.setPreferredSize(new Dimension(340, 46 + Math.min(6, Math.max(1, active.size())) * 62));

            Incident now = incidentList.getSelectedValue();
            String nowId = now == null ? null : now.id();
            if (!Objects.equals(nowId, activeId)) {
                applySelection();
            } else {
                renderHeader(now);
                loadMessages();
            }
        }, err -> setLive(false));
    }

    private void loadMessages() {
        Incident inc = incidentList.getSelectedValue();
        if (inc == null) return;
        String id = inc.id();
        BackgroundTask.run(() -> ctx.rest.select("incident_messages",
                "select=id,message,message_type,created_at,profiles(full_name,role)&incident_id=eq." + id
                        + "&order=created_at.asc"), rows -> {
            setLive(true);
            Incident current = incidentList.getSelectedValue();
            if (current == null || !current.id().equals(id)) return; // channel changed while loading
            int signature = rows == null ? 0 : rows.toString().hashCode();
            boolean firstLoad = !id.equals(shownChannelId);
            if (!firstLoad && signature == shownSignature) return;   // nothing new: keep the conversation untouched
            boolean wasAtBottom = isNearBottom();
            shownChannelId = id;
            shownSignature = signature;
            rebuildFeed(rows);
            if (firstLoad || wasAtBottom || forceScroll) scrollToBottom();
            forceScroll = false;
        }, err -> setLive(false));
    }

    private void sendMessage() {
        Incident inc = incidentList.getSelectedValue();
        String text = input.getText().trim();
        if (sending || inc == null || text.isEmpty()) return;
        String type = mode;
        if ("urgent".equals(type) && JOptionPane.showConfirmDialog(this,
                "Send an URGENT alert to everyone on " + inc.incidentNo() + "?",
                "Confirm urgent alert", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.YES_OPTION) return;

        sending = true;
        input.setEditable(false);
        refreshSendState();
        BackgroundTask.run(() -> {
            ctx.rest.rpc("send_incident_message", Map.of(
                    "p_incident", UUID.fromString(inc.id()),
                    "p_message", text,
                    "p_type", type));
            return Boolean.TRUE;
        }, ok -> {
            input.setText("");
            sending = false;
            input.setEditable(true);
            setMode("regular"); // urgent / announcement never stays selected by accident
            forceScroll = true;
            refreshSendState();
            input.requestFocusInWindow();
            loadMessages();
        }, err -> {
            sending = false;
            input.setEditable(true);
            refreshSendState();
            JOptionPane.showMessageDialog(this, "Message delivery failed: " + err.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void refreshNow() {
        refreshButton.setSpinning(true);
        loadIncidents();
        loadMessages();
        Timer stop = new Timer(900, e -> refreshButton.setSpinning(false));
        stop.setRepeats(false);
        stop.start();
    }

    // ------------------------------------------------------------------ UI state

    private void applySelection() {
        Incident inc = incidentList.getSelectedValue();
        activeId = inc == null ? null : inc.id();
        shownChannelId = null;
        lastRows = null;
        channelSelected = inc != null;
        renderHeader(inc);
        input.setEnabled(channelSelected);
        refreshSendState();
        if (inc == null) {
            showPlaceholder("Select an incident channel", "Use the channel button at the top right to choose an active incident.");
        } else {
            showPlaceholder("Loading messages\u2026", " ");
            loadMessages();
        }
    }

    private void renderHeader(Incident inc) {
        channelMeta.removeAll();
        if (inc == null) {
            channelTitle.setText("No channel selected");
            channelButton.setIcon(null);
            channelButton.setText(incidentModel.isEmpty() ? "No active incidents  \u25BE" : "Select channel  \u25BE");
        } else {
            channelTitle.setText(inc.incidentNo() + "  \u2014  " + inc.title());
            channelMeta.add(StatusBadge.severity(inc.severity()));
            channelMeta.add(StatusBadge.incidentStatus(inc.status().name().toLowerCase()));
            String t = String.valueOf(inc.title());
            if (t.length() > 34) t = t.substring(0, 33) + "\u2026";
            channelButton.setIcon(new DotIcon(GermsColors.forSeverity(inc.severity())));
            channelButton.setText(inc.incidentNo() + "  \u00B7  " + t + "  \u25BE");
        }
        channelMeta.revalidate();
        channelMeta.repaint();
        channelButton.revalidate();
    }

    private void setLive(boolean ok) {
        liveLabel.setText(ok ? "\u25CF Online" : "\u25CF Reconnecting\u2026");
        liveLabel.setForeground(ok ? Pal.GREEN : Pal.AMBER);
    }

    private void refreshSendState() {
        boolean hasText = !input.getText().isBlank();
        sendBtn.setEnabled(channelSelected && !sending && hasText);
    }

    private void setMode(String key) {
        mode = key;
        Color c = accentFor(key);
        sendBtn.setAccent(c);
        boolean special = !"regular".equals(key);
        modeTagWrap.setVisible(special);
        modeTag.setAccent(c);
        modeTag.setText(("urgent".equals(key) ? "URGENT" : "ANNOUNCEMENT") + "   \u2715");
        for (ModeOption o : options) if (o != null) o.setSelected(o.key.equals(key));
        composer.setFocused(input.hasFocus(), c);
        composer.revalidate();
        composer.repaint();
    }

    // ---------------------------------------------------------- sidebar animation

    private void setSidebarOpen(boolean open, boolean animate) {
        sidebarOpen = open;
        panelToggle.setActive(open);
        panelToggle.setToolTipText(open ? "Hide message types" : "Show message types");
        if (!animate) {
            slideTimer.stop();
            slide.setProgress(open ? 1 : 0);
            return;
        }
        slideFrom = slide.progress();
        slideTo = open ? 1 : 0;
        slideStart = System.nanoTime();
        slideTimer.start();
    }

    private void stepSlide() {
        double f = Math.min(1.0, (System.nanoTime() - slideStart) / (SLIDE_MS * 1_000_000.0));
        double eased = 1 - Math.pow(1 - f, 3);
        slide.setProgress(slideFrom + (slideTo - slideFrom) * eased);
        if (f >= 1.0) slideTimer.stop();
    }

    // ----------------------------------------------------------------------- feed

    private void onFeedResized() {
        int w = feedScroll.getViewport().getWidth();
        if (lastRows == null || w <= 0 || Math.abs(w - lastBuildWidth) < 40) return;
        boolean atBottom = isNearBottom();
        rebuildFeed(lastRows);
        if (atBottom) scrollToBottom();
    }

    private int maxBubbleText() {
        int vw = feedScroll.getViewport().getWidth();
        if (vw <= 0) return MAX_BUBBLE_TEXT;
        return Math.max(220, Math.min(MAX_BUBBLE_TEXT, (int) (vw * 0.66)));
    }

    private void rebuildFeed(JsonNode rows) {
        lastRows = rows;
        lastBuildWidth = feedScroll.getViewport().getWidth();
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            showPlaceholder("No messages yet", "Be the first to post an update on this incident.");
            return;
        }
        int maxText = maxBubbleText();
        feed.removeAll();
        LocalDate lastDay = null;
        for (JsonNode m : rows) {
            ZonedDateTime when = parseTime(m.path("created_at").asText());
            if (when != null && !when.toLocalDate().equals(lastDay)) {
                lastDay = when.toLocalDate();
                feed.add(dayRow(DAY_FMT.format(when)));
                feed.add(Box.createVerticalStrut(8));
            }
            JsonNode p = m.path("profiles");
            String sender = p.hasNonNull("full_name") ? p.path("full_name").asText() : "Command Staff";
            String role = roleLabel(p.path("role").asText(""));
            String type = m.path("message_type").asText("regular");
            boolean mine = sender.equals(user.fullName());
            String time = when == null ? "" : TIME_FMT.format(when);
            feed.add(new MessageRow(new MessageBubble(sender, role, time, type, m.path("message").asText(), mine, maxText), mine));
            feed.add(Box.createVerticalStrut(4));
        }
        feed.add(Box.createVerticalGlue());
        feed.revalidate();
        feed.repaint();
    }

    private void showPlaceholder(String head, String sub) {
        feed.removeAll();
        feed.add(Box.createVerticalGlue());
        JLabel l = new JLabel("<html><div style='text-align:center'><b style='font-size:14px'>" + head
                + "</b><br>" + sub + "</div></html>", SwingConstants.CENTER);
        l.setForeground(Pal.DIM);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        feed.add(l);
        feed.add(Box.createVerticalGlue());
        feed.revalidate();
        feed.repaint();
    }

    private static JComponent dayRow(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Pal.DIM);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11f));
        GlassPanel chip = new GlassPanel(new FlowLayout(FlowLayout.CENTER, 0, 0), 16, 24, 14, 40);
        chip.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        chip.add(l);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.add(chip);
        return row;
    }

    private static String roleLabel(String raw) {
        if (raw == null || raw.isBlank()) return "Staff";
        try {
            return Role.fromDb(raw).label();
        } catch (Exception e) {
            return StatusBadge.label(raw);
        }
    }

    private static ZonedDateTime parseTime(String raw) {
        try {
            return OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNearBottom() {
        JScrollBar bar = feedScroll.getVerticalScrollBar();
        return bar.getMaximum() - (bar.getValue() + bar.getVisibleAmount()) < 90;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = feedScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    private static String elide(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return s;
        String t = s;
        while (t.length() > 1 && fm.stringWidth(t + "\u2026") > width) t = t.substring(0, t.length() - 1);
        return t + "\u2026";
    }

    // ------------------------------------------------------------------- icons

    private static void drawIcon(Graphics2D g2, Kind kind, double cx, double cy, double size, Color color, boolean filled) {
        Graphics2D g = (Graphics2D) g2.create();
        smooth(g);
        g.translate(cx - size / 2, cy - size / 2);
        double s = size / 24.0;
        g.scale(s, s);
        g.setColor(color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case REFRESH -> {
                g.draw(new Arc2D.Double(4, 4, 16, 16, 40, 280, Arc2D.OPEN));
                double th = Math.toRadians(320);
                double px = 12 + 8 * Math.cos(th), py = 12 - 8 * Math.sin(th);
                double bx = Math.sin(th), by = Math.cos(th); // backwards along the arc
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double a = Math.toRadians(40) * sgn;
                    double wx = bx * Math.cos(a) - by * Math.sin(a);
                    double wy = bx * Math.sin(a) + by * Math.cos(a);
                    g.draw(new Line2D.Double(px, py, px + wx * 5, py + wy * 5));
                }
            }
            case SEND -> {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 11);
                p.lineTo(21, 3);
                p.lineTo(14, 21);
                p.lineTo(11, 13);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(11, 13, 21, 3));
            }
            case PANEL -> {
                g.draw(new RoundRectangle2D.Double(3, 4.5, 18, 15, 4, 4));
                g.draw(new Line2D.Double(14.5, 4.5, 14.5, 19.5));
                if (filled) {
                    g.setColor(alpha(color, 110));
                    g.fill(new Rectangle2D.Double(15.5, 5.5, 4.8, 13));
                }
            }
            case MEGAPHONE -> {
                Path2D p = new Path2D.Double();
                p.moveTo(4, 9);
                p.lineTo(9, 9);
                p.lineTo(19, 4);
                p.lineTo(19, 20);
                p.lineTo(9, 15);
                p.lineTo(4, 15);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(8, 15.5, 9.5, 20.5));
            }
            case WARNING -> {
                Path2D p = new Path2D.Double();
                p.moveTo(12, 3.5);
                p.lineTo(21.5, 20);
                p.lineTo(2.5, 20);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(12, 9.5, 12, 14));
                g.fill(new Ellipse2D.Double(11, 16, 2, 2));
            }
            case MESSAGE -> {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 13, 6, 6));
                Path2D tail = new Path2D.Double();
                tail.moveTo(8, 17);
                tail.lineTo(8, 21);
                tail.lineTo(12, 17);
                g.draw(tail);
            }
        }
        g.dispose();
    }

    private static final class DotIcon implements Icon {
        private final Color color;
        DotIcon(Color color) { this.color = color; }
        @Override public int getIconWidth() { return 12; }
        @Override public int getIconHeight() { return 12; }
        @Override public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(color);
            g2.fillOval(x + 1, y + 1, 9, 9);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------ custom pieces

    /** Round icon button with hover / pressed / disabled states; REFRESH can spin. */
    private static final class IconButton extends JButton {
        private final Kind kind;
        private final boolean solid;
        private Color accent;
        private boolean active;
        private double angle;
        private boolean stopRequested;
        private final Timer spin = new Timer(16, e -> {
            angle += 0.22;
            if (angle >= Math.PI * 2) {
                angle -= Math.PI * 2;
                if (stopRequested) { ((Timer) e.getSource()).stop(); angle = 0; stopRequested = false; }
            }
            repaint();
        });

        IconButton(Kind kind, Color accent, boolean solid, int size, String tip) {
            this.kind = kind;
            this.accent = accent;
            this.solid = solid;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(tip);
            getAccessibleContext().setAccessibleName(tip);
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
        }

        void setAccent(Color c) { this.accent = c; repaint(); }
        void setActive(boolean on) { this.active = on; repaint(); }

        void setSpinning(boolean on) {
            if (on) { stopRequested = false; if (!spin.isRunning()) spin.start(); }
            else if (spin.isRunning()) stopRequested = true; // finish the current turn, then stop
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight(), d = Math.min(w, h);
            int x = (w - d) / 2, y = (h - d) / 2;
            boolean on = isEnabled();
            boolean pressed = getModel().isPressed(), hover = getModel().isRollover();
            Color iconColor;
            if (solid) {
                if (on) {
                    Color base = pressed ? accent.darker() : hover ? accent.brighter() : accent;
                    glass(g2, x, y, d, d, d, alpha(base, 245), alpha(base.darker(), 235), alpha(Color.WHITE, 60), 1f);
                    iconColor = Color.WHITE;
                } else {
                    g2.setColor(new Color(255, 255, 255, 22));
                    g2.fillOval(x, y, d, d);
                    iconColor = new Color(255, 255, 255, 80);
                }
            } else {
                int a = !on ? 0 : pressed ? 64 : hover ? 40 : (active ? 34 : 16);
                g2.setColor(new Color(255, 255, 255, a));
                g2.fillOval(x, y, d, d);
                g2.setColor(new Color(255, 255, 255, active ? 70 : 36));
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(x, y, d - 1, d - 1);
                iconColor = on ? (active ? Color.WHITE : Pal.TEXT) : Pal.DIM;
            }
            if (kind == Kind.REFRESH) g2.rotate(angle, w / 2.0, h / 2.0);
            drawIcon(g2, kind, w / 2.0, h / 2.0, d * 0.48, iconColor, active);
            g2.dispose();
        }
    }

    /** Translucent pill button (channel picker and the active-mode tag). */
    private static final class GlassButton extends JButton {
        private Color accent = Color.WHITE;

        GlassButton() {
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setForeground(Pal.TEXT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            setIconTextGap(8);
        }

        void setAccent(Color c) { this.accent = c; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            boolean white = accent == Color.WHITE;
            int bump = getModel().isRollover() ? 14 : 0;
            glass(g2, 0, 0, getWidth(), getHeight(), getHeight(),
                    alpha(accent, (white ? 26 : 70) + bump), alpha(accent, (white ? 12 : 38) + bump),
                    alpha(accent, white ? 60 : 170), 1f);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Translucent rounded surface; the border brightens while the composer has focus. */
    private static class GlassPanel extends JPanel {
        private final int arc;
        private final int topAlpha;
        private final int bottomAlpha;
        private final int borderAlpha;
        private boolean focused;
        private Color focusColor = Pal.BLUE;

        GlassPanel(LayoutManager layout, int arc, int topAlpha, int bottomAlpha, int borderAlpha) {
            super(layout);
            this.arc = arc;
            this.topAlpha = topAlpha;
            this.bottomAlpha = bottomAlpha;
            this.borderAlpha = borderAlpha;
            setOpaque(false);
        }

        void setFocused(boolean f, Color c) {
            this.focused = f;
            this.focusColor = c;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            Color border = focused ? alpha(focusColor, 180) : new Color(255, 255, 255, borderAlpha);
            glass(g2, 0, 0, getWidth(), getHeight(), arc,
                    new Color(255, 255, 255, topAlpha), new Color(255, 255, 255, bottomAlpha), border, focused ? 1.6f : 1f);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class Divider extends JComponent {
        @Override public Dimension getPreferredSize() { return new Dimension(1, 1); }

        @Override protected void paintComponent(Graphics g) {
            g.setColor(Pal.BORDER);
            g.fillRect(0, 0, getWidth(), 1);
        }
    }

    /** Text area that paints its own placeholder, so it looks right on the dark glass in either app theme. */
    private static final class PlaceholderArea extends JTextArea {
        private String placeholder = "";

        PlaceholderArea(int rows, int cols) { super(rows, cols); }

        void setPlaceholder(String p) { this.placeholder = p; repaint(); }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getDocument().getLength() == 0 && !placeholder.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                smooth(g2);
                g2.setColor(isEnabled() ? new Color(147, 164, 191, 170) : new Color(147, 164, 191, 90));
                g2.setFont(getFont());
                Insets in = getInsets();
                g2.drawString(placeholder, in.left, in.top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    }

    /** Message column that always matches the viewport width. */
    private static final class FeedPanel extends JPanel implements Scrollable {
        FeedPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(40, r.height - 40); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Right-hand sidebar host: its width animates between 0 and the content width, sliding the content in from the edge. */
    private static final class SlideContainer extends JPanel {
        private final int full;
        private double progress = 1;

        SlideContainer(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            full = content.getPreferredSize().width;
            add(content, BorderLayout.EAST); // anchored to the right edge, so it slides rather than squashes
        }

        double progress() { return progress; }

        void setProgress(double p) {
            progress = Math.max(0, Math.min(1, p));
            setVisible(progress > 0.001);
            revalidate();
            repaint();
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension((int) Math.round(full * progress), d.height);
        }
    }

    /** One chat line: own messages hug the right edge, everyone else's the left. */
    private static final class MessageRow extends JPanel {
        MessageRow(MessageBubble bubble, boolean mine) {
            super(new BorderLayout());
            setOpaque(false);
            setAlignmentX(LEFT_ALIGNMENT);
            add(bubble, mine ? BorderLayout.EAST : BorderLayout.WEST);
        }

        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    /** Glass bubble: translucent gradient, hairline border, soft shadow. Sized to its text. */
    private static final class MessageBubble extends JPanel {
        private static final int M = 5; // room for the shadow around the bubble

        private final boolean mine;
        private final String type;

        MessageBubble(String sender, String role, String time, String type, String text, boolean mine, int maxText) {
            this.mine = mine;
            this.type = type == null ? "regular" : type.toLowerCase();
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(M + 8, M + 14, M + 8, M + 14));

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            header.setOpaque(false);
            header.setAlignmentX(LEFT_ALIGNMENT);
            if (special()) {
                JLabel tag = new JLabel(urgent() ? "URGENT" : "BROADCAST");
                tag.setFont(tag.getFont().deriveFont(Font.BOLD, 9f));
                tag.setForeground(accentFor(this.type));
                header.add(tag);
            }
            JLabel who = new JLabel("<html><span style='font-size:12px; color:#FFFFFF'><b>" + escape(sender) + "</b></span>"
                    + "<span style='font-size:9px; color:#93A4BF'>&nbsp;&nbsp;\u2022&nbsp;&nbsp;" + escape(role) + "</span></html>");
            header.add(who);

            JLabel body = new JLabel("<html>" + escape(text) + "</html>");
            body.setForeground(Pal.TEXT);
            body.setAlignmentX(LEFT_ALIGNMENT);
            // Fit the text; only force wrapping once a line would exceed the maximum bubble width.
            if (body.getPreferredSize().width > maxText) {
                body.setText("<html><body style='width:" + maxText + "px'>" + escape(text) + "</body></html>");
            }

            JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            footer.setOpaque(false);
            footer.setAlignmentX(LEFT_ALIGNMENT);
            JLabel ts = new JLabel(time);
            ts.setFont(ts.getFont().deriveFont(10f));
            ts.setForeground(Pal.DIM);
            footer.add(ts);

            add(header);
            add(Box.createVerticalStrut(3));
            add(body);
            add(Box.createVerticalStrut(2));
            add(footer);
        }

        private boolean urgent() { return "urgent".equals(type); }
        private boolean special() { return urgent() || "announcement".equals(type); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int x = M, y = M - 2, w = getWidth() - 2 * M, h = getHeight() - 2 * M;
            for (int i = 0; i < 4; i++) { // soft shadow
                g2.setColor(new Color(0, 0, 0, 16 - i * 3));
                g2.fillRoundRect(x - i, y + 2 + i, w + 2 * i, h, 20 + i * 2, 20 + i * 2);
            }
            boolean neutral = !special() && !mine;
            Color tint = special() ? accentFor(type) : mine ? Pal.BLUE : Color.WHITE;
            int topA = neutral ? 30 : (mine ? 92 : 70);
            int botA = neutral ? 13 : (mine ? 54 : 36);
            Color border = neutral ? new Color(255, 255, 255, 50) : alpha(tint, urgent() ? 210 : 140);
            glass(g2, x, y, w, h, 20, alpha(tint, topA), alpha(tint, botA), border, urgent() ? 1.6f : 1f);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Selectable message-type card in the right sidebar. */
    private static final class ModeOption extends JToggleButton {
        private final String key;
        private final Kind kind;
        private final String title;
        private final String subtitle;
        private final Color color;

        ModeOption(String key, Kind kind, String title, String subtitle, Color color) {
            this.key = key;
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
            this.color = color;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(subtitle);
            getAccessibleContext().setAccessibleName(title);
            Dimension d = new Dimension(224, 62);
            setPreferredSize(d);
            setMaximumSize(d);
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int w = getWidth(), h = getHeight();
            boolean sel = isSelected(), hover = getModel().isRollover();
            boolean urgent = "urgent".equals(key);
            int ta = sel ? 78 : urgent ? 32 : 18;
            int ba = sel ? 42 : urgent ? 16 : 8;
            if (hover && !sel) { ta += 14; ba += 8; }
            Color border = sel ? alpha(color, 220) : urgent ? alpha(color, 120) : new Color(255, 255, 255, 36);
            glass(g2, 0, 0, w, h, 16, alpha(color, ta), alpha(color, ba), border, sel ? 1.6f : 1f);

            g2.setColor(alpha(color, 46));
            g2.fillOval(14, h / 2 - 17, 34, 34);
            drawIcon(g2, kind, 31, h / 2.0, 18, color, false);

            Font tf = getFont().deriveFont(Font.BOLD, 13f);
            g2.setFont(tf);
            g2.setColor(Pal.TEXT);
            int textW = w - 60 - (sel ? 28 : 14);
            g2.drawString(elide(title, g2.getFontMetrics(), textW), 60, h / 2 - 3);
            Font sf = getFont().deriveFont(Font.PLAIN, 11f);
            g2.setFont(sf);
            g2.setColor(Pal.DIM);
            g2.drawString(elide(subtitle, g2.getFontMetrics(), textW), 60, h / 2 + 13);

            if (sel) {
                g2.setColor(alpha(color, 235));
                g2.fillOval(w - 24, h / 2 - 5, 10, 10);
            }
            g2.dispose();
        }
    }

    /** Incident entry in the channel popup: severity stripe, number, title, status. */
    private static final class IncidentCell extends JPanel implements ListCellRenderer<Incident> {
        private final JLabel no = new JLabel();
        private final JLabel title = new JLabel();
        private final JLabel status = new JLabel();
        private Color stripe = Pal.BLUE;
        private boolean selected;

        IncidentCell() {
            super(new BorderLayout(0, 3));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 14));
            no.setFont(no.getFont().deriveFont(Font.BOLD, 12f));
            status.setFont(status.getFont().deriveFont(Font.BOLD, 11f));
            title.setForeground(Pal.TEXT);
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(no, BorderLayout.WEST);
            top.add(status, BorderLayout.EAST);
            add(top, BorderLayout.NORTH);
            add(title, BorderLayout.CENTER);
        }

        @Override public Component getListCellRendererComponent(JList<? extends Incident> list, Incident inc,
                                                                 int index, boolean isSelected, boolean hasFocus) {
            stripe = GermsColors.forSeverity(inc.severity());
            selected = isSelected;
            no.setText(String.valueOf(inc.incidentNo()));
            no.setForeground(stripe);
            title.setText(inc.title());
            status.setText(inc.status().label());
            status.setForeground(GermsColors.forIncidentStatus(inc.status().name().toLowerCase()));
            return this;
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(10, Math.max(d.height, 58)); // narrow width keeps the list tracking the viewport
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            if (selected) {
                g2.setColor(new Color(255, 255, 255, 26));
                g2.fillRoundRect(6, 2, getWidth() - 12, getHeight() - 4, 12, 12);
            }
            g2.setColor(stripe);
            g2.fillRoundRect(8, 10, 4, getHeight() - 20, 4, 4);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}