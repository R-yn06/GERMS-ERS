package com.gudo.germs.ui.components;

import com.gudo.germs.ui.theme.GermsColors;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Interactive OpenStreetMap Swing Component for GERMS.
 * Uses OpenStreetMap standard Slippy Map tiles with memory + disk caching.
 * Supports pan, zoom, click-to-pin, search geocoding, and differentiated markers for
 * incidents, active responders, evacuation shelters, and emergency resources.
 */
public final class OpenStreetMapPanel extends JPanel {
    public enum MarkerType {
        INCIDENT(new Color(200, 16, 46), "Incident"),
        RESPONDER(new Color(0, 114, 206), "Responder"),
        EVACUATION(new Color(16, 124, 65), "Evacuation Center"),
        RESOURCE(new Color(216, 88, 12), "Emergency Resource"),
        SELECTED_PIN(new Color(128, 0, 128), "Selected Coordinate");

        public final Color color;
        public final String defaultLabel;
        MarkerType(Color color, String defaultLabel) {
            this.color = color;
            this.defaultLabel = defaultLabel;
        }
    }

    public record MapMarker(String id, String title, double lat, double lon, MarkerType type, String details) {}

    private static final int TILE_SIZE = 256;
    private static final double DEFAULT_LAT = 7.0731;  // Davao City Center
    private static final double DEFAULT_LON = 125.6128;
    private static final int DEFAULT_ZOOM = 13;
    private static final String USER_AGENT = "GERMS-Emergency-Response-System/1.0 (PRC Davao Operations)";

    private double centerLat = DEFAULT_LAT;
    private double centerLon = DEFAULT_LON;
    private int zoom = DEFAULT_ZOOM;

    private final List<MapMarker> markers = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, BufferedImage> memoryCache = new ConcurrentHashMap<>();
    private final Path diskCacheDir;
    private final ExecutorService tileLoader = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "GERMS-MapTileLoader");
        t.setDaemon(true);
        return t;
    });

    private Point dragStart = null;
    private boolean pinDropEnabled = false;
    private Consumer<Point2D.Double> onCoordinatePicked;
    private Point2D.Double selectedCoord = null;
    private MapMarker hoveredMarker = null;

    public OpenStreetMapPanel() {
        this(DEFAULT_LAT, DEFAULT_LON, DEFAULT_ZOOM);
    }

    public OpenStreetMapPanel(double initialLat, double initialLon, int initialZoom) {
        this.centerLat = initialLat;
        this.centerLon = initialLon;
        this.zoom = Math.max(1, Math.min(18, initialZoom));

        Path cache = Path.of(System.getProperty("java.io.tmpdir"), "germs_osm_tiles");
        try {
            Files.createDirectories(cache);
        } catch (Exception ignored) {}
        this.diskCacheDir = cache;

        setBackground(new Color(225, 228, 232));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        initMouseControls();
    }

    public void setPinDropEnabled(boolean enabled, Consumer<Point2D.Double> onPicked) {
        this.pinDropEnabled = enabled;
        this.onCoordinatePicked = onPicked;
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.CROSSHAIR_CURSOR : Cursor.HAND_CURSOR));
    }

    public void setCenter(double lat, double lon, int newZoom) {
        this.centerLat = lat;
        this.centerLon = lon;
        this.zoom = Math.max(1, Math.min(18, newZoom));
        repaint();
    }

    public void addMarker(MapMarker marker) {
        markers.add(marker);
        repaint();
    }

    public void setMarkers(List<MapMarker> newMarkers) {
        markers.clear();
        if (newMarkers != null) {
            markers.addAll(newMarkers);
        }
        repaint();
    }

    public void clearMarkers() {
        markers.clear();
        repaint();
    }

    public void setSelectedCoordinate(double lat, double lon) {
        this.selectedCoord = new Point2D.Double(lat, lon);
        repaint();
    }

    public Point2D.Double getSelectedCoordinate() {
        return selectedCoord;
    }

    private void initMouseControls() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragStart = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStart = null;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && pinDropEnabled) {
                    Point2D.Double coord = screenToLatLon(e.getX(), e.getY());
                    selectedCoord = coord;
                    if (onCoordinatePicked != null) {
                        onCoordinatePicked.accept(coord);
                    }
                    repaint();
                } else if (e.getClickCount() == 2) {
                    // Double click to zoom in centered on click point
                    Point2D.Double coord = screenToLatLon(e.getX(), e.getY());
                    centerLat = coord.x;
                    centerLon = coord.y;
                    if (zoom < 18) zoom++;
                    repaint();
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    int dx = e.getX() - dragStart.x;
                    int dy = e.getY() - dragStart.y;
                    dragStart = e.getPoint();

                    Point2D.Double centerPixel = latLonToPixel(centerLat, centerLon, zoom);
                    Point2D.Double newCenterPixel = new Point2D.Double(centerPixel.x - dx, centerPixel.y - dy);
                    Point2D.Double newCenter = pixelToLatLon(newCenterPixel.x, newCenterPixel.y, zoom);

                    centerLat = Math.max(-85.0511, Math.min(85.0511, newCenter.x));
                    centerLon = ((newCenter.y + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                // Check if hovering over any marker
                MapMarker found = null;
                for (MapMarker m : markers) {
                    Point p = latLonToScreen(m.lat(), m.lon());
                    if (p != null && Math.abs(p.x - e.getX()) <= 12 && Math.abs(p.y - e.getY()) <= 18) {
                        found = m;
                        break;
                    }
                }
                if (found != hoveredMarker) {
                    hoveredMarker = found;
                    repaint();
                }
            }
        });

        addMouseWheelListener(e -> {
            int rotation = e.getWheelRotation();
            if (rotation < 0) {
                if (zoom < 18) {
                    // Zoom in towards mouse cursor
                    Point2D.Double mouseCoord = screenToLatLon(e.getX(), e.getY());
                    zoom++;
                    centerLat = (centerLat + mouseCoord.x) / 2.0;
                    centerLon = (centerLon + mouseCoord.y) / 2.0;
                    repaint();
                }
            } else if (rotation > 0) {
                if (zoom > 2) {
                    zoom--;
                    repaint();
                }
            }
        });
    }

    public void zoomIn() {
        if (zoom < 18) {
            zoom++;
            repaint();
        }
    }

    public void zoomOut() {
        if (zoom > 2) {
            zoom--;
            repaint();
        }
    }

    public void resetView() {
        this.centerLat = DEFAULT_LAT;
        this.centerLon = DEFAULT_LON;
        this.zoom = DEFAULT_ZOOM;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int width = getWidth();
        int height = getHeight();

        Point2D.Double centerPx = latLonToPixel(centerLat, centerLon, zoom);
        double startX = centerPx.x - (double) width / 2;
        double startY = centerPx.y - (double) height / 2;

        int minTileX = (int) Math.floor(startX / TILE_SIZE);
        int maxTileX = (int) Math.floor((startX + width) / TILE_SIZE);
        int minTileY = (int) Math.floor(startY / TILE_SIZE);
        int maxTileY = (int) Math.floor((startY + height) / TILE_SIZE);

        int maxTilesAtZoom = 1 << zoom;

        // 1. Draw tiles
        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                if (ty < 0 || ty >= maxTilesAtZoom) continue;
                int wrappedX = ((tx % maxTilesAtZoom) + maxTilesAtZoom) % maxTilesAtZoom;

                int screenX = (int) Math.round((double) tx * TILE_SIZE - startX);
                int screenY = (int) Math.round((double) ty * TILE_SIZE - startY);

                BufferedImage tile = getTile(zoom, wrappedX, ty);
                if (tile != null) {
                    g2.drawImage(tile, screenX, screenY, TILE_SIZE, TILE_SIZE, null);
                } else {
                    g2.setColor(new Color(235, 237, 240));
                    g2.fillRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                    g2.setColor(new Color(215, 218, 222));
                    g2.drawRect(screenX, screenY, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // 2. Draw markers
        for (MapMarker m : markers) {
            drawMarker(g2, m, m == hoveredMarker);
        }

        // 3. Draw user-selected pin if active
        if (selectedCoord != null) {
            drawCustomPin(g2, selectedCoord.x, selectedCoord.y, MarkerType.SELECTED_PIN.color, "Selected Target");
        }

        // 4. Draw attribution & controls overlay
        drawOverlay(g2, width, height);

        g2.dispose();
    }

    private void drawMarker(Graphics2D g2, MapMarker m, boolean hovered) {
        Point p = latLonToScreen(m.lat(), m.lon());
        if (p == null) return;

        Color c = m.type().color;
        int size = hovered ? 18 : 14;

        // Pin drop shape
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillOval(p.x - size / 2 + 2, p.y - 2, size, 6); // shadow

        g2.setColor(c);
        g2.fillOval(p.x - size / 2, p.y - size - 8, size, size);
        int[] xp = {p.x - size / 2, p.x + size / 2, p.x};
        int[] yp = {p.y - size / 2 - 8, p.y - size / 2 - 8, p.y};
        g2.fillPolygon(xp, yp, 3);

        g2.setColor(Color.WHITE);
        g2.fillOval(p.x - 3, p.y - size - 4, 6, 6);

        // Marker label
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(m.title());

        int bx = p.x - tw / 2 - 4;
        int by = p.y - size - 26;

        g2.setColor(new Color(30, 30, 30, 200));
        g2.fillRoundRect(bx, by, tw + 8, 16, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(m.title(), bx + 4, by + 12);

        // Hover tooltip popup
        if (hovered && m.details() != null && !m.details().isBlank()) {
            g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
            FontMetrics dfm = g2.getFontMetrics();
            int dw = dfm.stringWidth(m.details());
            int dbx = p.x - dw / 2 - 6;
            int dby = by - 20;

            g2.setColor(new Color(15, 23, 42, 235));
            g2.fillRoundRect(dbx, dby, dw + 12, 18, 6, 6);
            g2.setColor(new Color(254, 240, 138));
            g2.drawString(m.details(), dbx + 6, dby + 13);
        }
    }

    private void drawCustomPin(Graphics2D g2, double lat, double lon, Color c, String label) {
        Point p = latLonToScreen(lat, lon);
        if (p == null) return;

        int size = 16;
        g2.setColor(new Color(0, 0, 0, 90));
        g2.fillOval(p.x - 6, p.y - 2, 12, 6);

        g2.setColor(c);
        g2.fillOval(p.x - size / 2, p.y - size - 8, size, size);
        int[] xp = {p.x - size / 2, p.x + size / 2, p.x};
        int[] yp = {p.y - size / 2 - 8, p.y - size / 2 - 8, p.y};
        g2.fillPolygon(xp, yp, 3);

        g2.setColor(Color.WHITE);
        g2.fillOval(p.x - 3, p.y - size - 4, 6, 6);

        String text = String.format(Locale.ROOT, "%s (%.5f, %.5f)", label, lat, lon);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);
        int bx = p.x - tw / 2 - 4;
        int by = p.y - size - 26;

        g2.setColor(new Color(88, 28, 135, 220));
        g2.fillRoundRect(bx, by, tw + 8, 16, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(text, bx + 4, by + 12);
    }

    private void drawOverlay(Graphics2D g2, int width, int height) {
        // Attribution bar
        String attr = "\u00A9 OpenStreetMap contributors | GERMS Emergency GIS";
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        FontMetrics fm = g2.getFontMetrics();
        int aw = fm.stringWidth(attr);
        g2.setColor(new Color(255, 255, 255, 190));
        g2.fillRect(width - aw - 16, height - 20, aw + 16, 20);
        g2.setColor(new Color(70, 70, 70));
        g2.drawString(attr, width - aw - 8, height - 6);

        // Zoom & Coordinate HUD
        String hud = String.format(Locale.ROOT, "Zoom %d  \u00B7  Center: %.4f\u00B0, %.4f\u00B0", zoom, centerLat, centerLon);
        if (pinDropEnabled) {
            hud += "  \u00B7  [Click map to drop pin]";
        }
        int hw = fm.stringWidth(hud);
        g2.setColor(new Color(20, 20, 20, 180));
        g2.fillRoundRect(10, height - 26, hw + 16, 20, 6, 6);
        g2.setColor(Color.WHITE);
        g2.drawString(hud, 18, height - 12);
    }

    private Point latLonToScreen(double lat, double lon) {
        Point2D.Double p = latLonToPixel(lat, lon, zoom);
        Point2D.Double centerPx = latLonToPixel(centerLat, centerLon, zoom);
        int sx = (int) Math.round(p.x - centerPx.x + (double) getWidth() / 2);
        int sy = (int) Math.round(p.y - centerPx.y + (double) getHeight() / 2);
        return new Point(sx, sy);
    }

    private Point2D.Double screenToLatLon(int sx, int sy) {
        Point2D.Double centerPx = latLonToPixel(centerLat, centerLon, zoom);
        double px = centerPx.x + (sx - (double) getWidth() / 2);
        double py = centerPx.y + (sy - (double) getHeight() / 2);
        return pixelToLatLon(px, py, zoom);
    }

    private static Point2D.Double latLonToPixel(double lat, double lon, int zoom) {
        double x = (lon + 180.0) / 360.0 * (1 << zoom) * TILE_SIZE;
        double sinLat = Math.sin(Math.toRadians(lat));
        sinLat = Math.max(-0.9999, Math.min(0.9999, sinLat));
        double y = (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * (1 << zoom) * TILE_SIZE;
        return new Point2D.Double(x, y);
    }

    private static Point2D.Double pixelToLatLon(double px, double py, int zoom) {
        double mapSize = (double) (1 << zoom) * TILE_SIZE;
        double lon = (px / mapSize) * 360.0 - 180.0;
        double n = Math.PI - 2.0 * Math.PI * (py / mapSize);
        double lat = Math.toDegrees(Math.atan(Math.sinh(n)));
        return new Point2D.Double(lat, lon);
    }

    private BufferedImage getTile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;
        BufferedImage mem = memoryCache.get(key);
        if (mem != null) return mem;

        File diskFile = diskCacheDir.resolve(z + "_" + x + "_" + y + ".png").toFile();
        if (diskFile.exists()) {
            try {
                BufferedImage img = ImageIO.read(diskFile);
                if (img != null) {
                    memoryCache.put(key, img);
                    return img;
                }
            } catch (Exception ignored) {}
        }

        // Asynchronously download tile
        tileLoader.submit(() -> fetchTile(z, x, y, key, diskFile));
        return null;
    }

    private void fetchTile(int z, int x, int y, String key, File diskFile) {
        try {
            String urlStr = String.format(Locale.ROOT, "https://tile.openstreetmap.org/%d/%d/%d.png", z, x, y);
            URI uri = URI.create(urlStr);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null) {
                        memoryCache.put(key, img);
                        try {
                            ImageIO.write(img, "png", diskFile);
                        } catch (Exception ignored) {}
                        SwingUtilities.invokeLater(this::repaint);
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}

