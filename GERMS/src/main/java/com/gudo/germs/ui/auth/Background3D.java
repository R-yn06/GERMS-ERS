package com.gudo.germs.ui.auth;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Pure Java2D 3D Reactive Engine & Background Canvas.
 * No OpenGL / native libraries required:
 * - CPU vector matrix math, 3D-to-2D perspective projection, and Lambertian shading.
 * - Painter's algorithm depth sorting for translucent glass polyhedra.
 * - Interactive mouse parallax tilt, drag-to-spin inertia, and expanding liquid ripples.
 * - Immediate pause on login to eliminate CPU load.
 */
public class Background3D extends JPanel {

    private final Timer renderTimer;
    private boolean isPaused = false;
    private boolean isDarkTheme;
    private Color accentColor;

    // Camera & Mouse tracking
    private double targetRotX = 0.25, targetRotY = -0.35;
    private double currentRotX = 0.25, currentRotY = -0.35;
    private double mouseOffsetX = 0, mouseOffsetY = 0;
    private double angularVelX = 0, angularVelY = 0;
    private Point lastMousePos = null;
    

    // Ripple effects
    private static class Ripple {
        double x, y;
        double radius = 0;
        double maxRadius = 380;
        double alpha = 0.65;
    }
    private final List<Ripple> ripples = Collections.synchronizedList(new ArrayList<>());

    // Geometry: Floating 3D Polygonal Meshes
    private final List<Mesh3D> meshes = new ArrayList<>();

    public Background3D(boolean isDarkTheme, Color accentColor) {
        this.isDarkTheme = isDarkTheme;
        this.accentColor = accentColor;
        setOpaque(false);

        initGeometries();

        // Mouse listeners for Tilt, Drag Spin, and Click Ripples
        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePos = e.getPoint();
                Ripple r = new Ripple();
                r.x = e.getX();
                r.y = e.getY();
                ripples.add(r);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMousePos != null) {
                    double dx = e.getX() - lastMousePos.x;
                    double dy = e.getY() - lastMousePos.y;
                    angularVelY = dx * 0.008;
                    angularVelX = -dy * 0.008;
                    targetRotY += angularVelY;
                    targetRotX += angularVelX;
                    lastMousePos = e.getPoint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                double cx = getWidth() / 2.0;
                double cy = getHeight() / 2.0;
                if (cx > 0 && cy > 0) {
                    mouseOffsetX = (e.getX() - cx) / cx * 0.15;
                    mouseOffsetY = (e.getY() - cy) / cy * 0.15;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                lastMousePos = null;
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);

        // 60 FPS Render Loop (16ms)
        renderTimer = new Timer(16, e -> {
            if (!isPaused) {
                updatePhysics();
                repaint();
            }
        });
        renderTimer.start();
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            renderTimer.stop();
        } else {
            renderTimer.start();
        }
    }

    public void setAccentColor(Color accentColor) {
        this.accentColor = accentColor;
    }

    private void updatePhysics() {
        targetRotY += angularVelY;
        targetRotX += angularVelX;
        angularVelX *= 0.94;
        angularVelY *= 0.94;

        currentRotX += (targetRotX + mouseOffsetY - currentRotX) * 0.08;
        currentRotY += (targetRotY + mouseOffsetX - currentRotY) * 0.08;

        // Subtle ambient continuous rotation
        targetRotY += 0.002;

        synchronized (ripples) {
            ripples.removeIf(r -> {
                r.radius += 12;
                r.alpha *= 0.93;
                return r.alpha <= 0.02 || r.radius >= r.maxRadius;
            });
        }
    }

    private void initGeometries() {
        meshes.add(createIcosahedron(0, 0, 0, 180));
        meshes.add(createFacetedRing(-260, -120, -100, 90));
        meshes.add(createFacetedRing(280, 140, -120, 110));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        paintAtmosphericGradient(g2, w, h);
        renderScene3D(g2, w, h);
        paintRipples(g2);

        g2.dispose();
    }

    private void paintAtmosphericGradient(Graphics2D g2, int w, int h) {
        Point p1 = new Point(0, 0);
        Point p2 = new Point(w, h);
        Color top, bottom;

        if (isDarkTheme) {
            top = new Color(18, 20, 26);
            bottom = new Color(8, 10, 14);
        } else {
            top = new Color(242, 245, 250);
            bottom = new Color(220, 226, 238);
        }

        g2.setPaint(new GradientPaint(p1, top, p2, bottom));
        g2.fillRect(0, 0, w, h);

        int glowSize = Math.max(w, h);
        float[] dist = {0.0f, 0.5f, 1.0f};
        Color cMid = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), isDarkTheme ? 40 : 25);
        Color cFade = new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 0);
        Color[] colors = {cMid, cFade, cFade};

        RadialGradientPaint glow = new RadialGradientPaint(
                w / 2.0f + (float) (mouseOffsetX * 150),
                h / 2.0f + (float) (mouseOffsetY * 150),
                glowSize * 0.45f,
                dist,
                colors
        );
        g2.setPaint(glow);
        g2.fillRect(0, 0, w, h);
    }

    private void renderScene3D(Graphics2D g2, int w, int h) {
        double fov = 650.0;
        double camDist = 580.0;
        double cosX = Math.cos(currentRotX), sinX = Math.sin(currentRotX);
        double cosY = Math.cos(currentRotY), sinY = Math.sin(currentRotY);

        List<FacePolygon> renderList = new ArrayList<>();
        double lx = 0.577, ly = -0.577, lz = 0.577; // Normalized directional light

        for (Mesh3D mesh : meshes) {
            for (Face3D face : mesh.faces) {
                Point3D[] t = new Point3D[face.indices.length];
                boolean behindCam = false;

                for (int i = 0; i < face.indices.length; i++) {
                    Point3D v = mesh.vertices[face.indices[i]];
                    double x1 = v.x * cosY + v.z * sinY;
                    double z1 = -v.x * sinY + v.z * cosY;
                    double y2 = v.y * cosX - z1 * sinX;
                    double z2 = v.y * sinX + z1 * cosX + camDist;

                    if (z2 <= 20) { behindCam = true; break; }
                    t[i] = new Point3D(x1, y2, z2);
                }

                if (behindCam) continue;

                // Normal computation for backface culling & Lambertian shading
                double ax = t[1].x - t[0].x, ay = t[1].y - t[0].y, az = t[1].z - t[0].z;
                double bx = t[2].x - t[0].x, by = t[2].y - t[0].y, bz = t[2].z - t[0].z;
                double nx = ay * bz - az * by;
                double ny = az * bx - ax * bz;
                double nz = ax * by - ay * bx;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);

                if (len == 0) continue;
                nx /= len; ny /= len; nz /= len;

                // Backface culling
                if (nz >= 0) continue;

                double diffuse = Math.max(0.1, -(nx * lx + ny * ly + nz * lz));
                double avgZ = (t[0].z + t[1].z + t[2].z) / 3.0;

                Path2D path = new Path2D.Double();
                for (int i = 0; i < t.length; i++) {
                    double projX = (t[i].x * fov) / t[i].z + w / 2.0;
                    double projY = (t[i].y * fov) / t[i].z + h / 2.0;
                    if (i == 0) path.moveTo(projX, projY);
                    else path.lineTo(projX, projY);
                }
                path.closePath();

                renderList.add(new FacePolygon(path, avgZ, diffuse));
            }
        }

        // Painter's Algorithm: Depth Sort
        renderList.sort((f1, f2) -> Double.compare(f2.avgZ, f1.avgZ));

        for (FacePolygon fp : renderList) {
            float bright = (float) Math.min(1.0, fp.diffuse * 1.25);
            int alpha = isDarkTheme ? (int) (50 + fp.diffuse * 45) : (int) (30 + fp.diffuse * 35);
            Color fill = new Color(
                    Math.min(255, (int) (accentColor.getRed() * bright * 0.4 + (isDarkTheme ? 40 : 210) * 0.6)),
                    Math.min(255, (int) (accentColor.getGreen() * bright * 0.4 + (isDarkTheme ? 50 : 220) * 0.6)),
                    Math.min(255, (int) (accentColor.getBlue() * bright * 0.4 + (isDarkTheme ? 70 : 240) * 0.6)),
                    alpha
            );

            g2.setColor(fill);
            g2.fill(fp.path);

            g2.setColor(new Color(255, 255, 255, isDarkTheme ? 30 : 50));
            g2.setStroke(new BasicStroke(1.0f));
            g2.draw(fp.path);
        }
    }

    private void paintRipples(Graphics2D g2) {
        synchronized (ripples) {
            for (Ripple r : ripples) {
                int a = (int) (r.alpha * 255);
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), a));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawOval((int) (r.x - r.radius), (int) (r.y - r.radius), (int) (r.radius * 2), (int) (r.radius * 2));
            }
        }
    }

    // Geometry Helpers
    private static class Point3D { double x, y, z; Point3D(double x, double y, double z) { this.x = x; this.y = y; this.z = z; } }
    private static class Face3D { int[] indices; Face3D(int... idx) { this.indices = idx; } }
    private static class Mesh3D { Point3D[] vertices; Face3D[] faces; }
    private static class FacePolygon { Path2D path; double avgZ; double diffuse; FacePolygon(Path2D p, double z, double d) { path = p; avgZ = z; diffuse = d; } }

    private Mesh3D createIcosahedron(double ox, double oy, double oz, double scale) {
        Mesh3D m = new Mesh3D();
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        double a = scale / Math.sqrt(1 + phi * phi);
        double b = a * phi;

        m.vertices = new Point3D[]{
                new Point3D(ox - a, oy + b, oz), new Point3D(ox + a, oy + b, oz), new Point3D(ox - a, oy - b, oz), new Point3D(ox + a, oy - b, oz),
                new Point3D(ox, oy - a, oz + b), new Point3D(ox, oy + a, oz + b), new Point3D(ox, oy - a, oz - b), new Point3D(ox, oy + a, oz - b),
                new Point3D(ox + b, oy, oz - a), new Point3D(ox + b, oy, oz + a), new Point3D(ox - b, oy, oz - a), new Point3D(ox - b, oy, oz + a)
        };

        m.faces = new Face3D[]{
                new Face3D(0, 11, 5), new Face3D(0, 5, 1), new Face3D(0, 1, 7), new Face3D(0, 7, 10), new Face3D(0, 10, 11),
                new Face3D(1, 5, 9), new Face3D(5, 11, 4), new Face3D(11, 10, 2), new Face3D(10, 7, 6), new Face3D(7, 1, 8),
                new Face3D(3, 9, 4), new Face3D(3, 4, 2), new Face3D(3, 2, 6), new Face3D(3, 6, 8), new Face3D(3, 8, 9),
                new Face3D(4, 9, 5), new Face3D(2, 4, 11), new Face3D(6, 2, 10), new Face3D(8, 6, 7), new Face3D(9, 8, 1)
        };
        return m;
    }

    private Mesh3D createFacetedRing(double ox, double oy, double oz, double scale) {
        Mesh3D m = new Mesh3D();
        int segments = 8;
        m.vertices = new Point3D[segments * 2];
        for (int i = 0; i < segments; i++) {
            double angle = (2 * Math.PI / segments) * i;
            m.vertices[i] = new Point3D(ox + Math.cos(angle) * scale, oy + Math.sin(angle) * scale, oz - 20);
            m.vertices[i + segments] = new Point3D(ox + Math.cos(angle) * (scale * 0.7), oy + Math.sin(angle) * (scale * 0.7), oz + 20);
        }
        m.faces = new Face3D[segments * 2];
        for (int i = 0; i < segments; i++) {
            int next = (i + 1) % segments;
            m.faces[i * 2] = new Face3D(i, next, i + segments);
            m.faces[i * 2 + 1] = new Face3D(next, next + segments, i + segments);
        }
        return m;
    }
    public void updateTheme(boolean isDark) {
        this.isDarkTheme = isDark;
        repaint();
    }
}