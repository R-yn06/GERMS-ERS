package com.gudo.germs.ui.components;

import com.gudo.germs.services.GeoLocationService;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Reusable map-based location picker for reports, facilities, and responder location sharing. */
public final class LocationPickerDialog {
    public record Selection(double latitude, double longitude, String label, String source,
                            Double accuracyMeters) {}

    private LocationPickerDialog() {}

    public static Selection showDialog(Window owner, String title, Selection initialSelection) {
        return new Picker(owner, title, initialSelection).show();
    }

    private static final class Picker {
        private final JDialog dialog;
        private final OpenStreetMapPanel map;
        private final JTextField search = new JTextField(24);
        private final JComboBox<GeoLocationService.SearchResult> results = new JComboBox<>();
        private final JLabel status = new JLabel("Search a place or click the map to pin a location.");
        private final JLabel selectionText = new JLabel("No location selected");
        private final JButton choose = new JButton("Use Selected Location");
        private Selection selection;
        private List<GeoLocationService.SearchResult> searchResults = List.of();

        private Picker(Window owner, String title, Selection initialSelection) {
            dialog = new JDialog(owner, title, JDialog.ModalityType.APPLICATION_MODAL);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            double latitude = initialSelection == null ? GeoLocationService.DEFAULT_LAT : initialSelection.latitude();
            double longitude = initialSelection == null ? GeoLocationService.DEFAULT_LON : initialSelection.longitude();
            map = new OpenStreetMapPanel(latitude, longitude, initialSelection == null ? 12 : 15);
            if (initialSelection != null) {
                selection = initialSelection;
                map.setSelectedCoordinate(latitude, longitude);
                updateSelectionText();
            }
            map.setPinDropEnabled(true, this::pin);
        }

        private Selection show() {
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            JButton find = new JButton("Search");
            JButton approximate = new JButton("Use Approximate Network Location");
            controls.add(new JLabel("Landmark or address:"));
            controls.add(search);
            controls.add(find);
            controls.add(results);
            controls.add(approximate);

            find.addActionListener(event -> searchPlaces());
            search.addActionListener(event -> searchPlaces());
            results.addActionListener(event -> centerOnResult());
            approximate.addActionListener(event -> useApproximateLocation());

            JPanel footer = new JPanel(new BorderLayout(8, 4));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(event -> dialog.dispose());
            choose.setEnabled(selection != null);
            choose.addActionListener(event -> dialog.dispose());
            buttons.add(cancel);
            buttons.add(choose);
            JPanel text = new JPanel(new BorderLayout(0, 3));
            text.add(selectionText, BorderLayout.NORTH);
            text.add(status, BorderLayout.SOUTH);
            footer.add(text, BorderLayout.CENTER);
            footer.add(buttons, BorderLayout.EAST);

            dialog.setLayout(new BorderLayout(0, 6));
            dialog.add(controls, BorderLayout.NORTH);
            dialog.add(map, BorderLayout.CENTER);
            dialog.add(footer, BorderLayout.SOUTH);
            dialog.setPreferredSize(new Dimension(1050, 680));
            dialog.pack();
            dialog.setLocationRelativeTo(dialog.getOwner());
            dialog.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent event) {
                    map.setPinDropEnabled(false, null);
                    map.shutdownTileLoader();
                }
            });
            dialog.setVisible(true);
            return selection;
        }

        private void pin(Point2D.Double point) {
            selection = new Selection(point.x, point.y, "Pinned map location", "manual", null);
            updateSelectionText();
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    try {
                        return GeoLocationService.reverseGeocode(point.x, point.y);
                    } catch (Exception e) {
                        return null;
                    }
                }

                @Override protected void done() {
                    if (!dialog.isDisplayable() || selection == null
                            || selection.latitude() != point.x || selection.longitude() != point.y) return;
                    try {
                        String address = get();
                        if (address != null && !address.isBlank()) {
                            selection = new Selection(point.x, point.y, address, "manual", null);
                            updateSelectionText();
                        }
                    } catch (Exception error) {
                        status.setText("Pinned coordinates selected; address lookup failed: " + error.getMessage());
                    }
                }
            }.execute();
        }

        private void searchPlaces() {
            String query = search.getText().trim();
            if (query.length() < 2) {
                status.setText("Enter at least two characters to search.");
                return;
            }
            status.setText("Searching OpenStreetMap and local landmarks...");
            new SwingWorker<GeoLocationService.SearchResponse, Void>() {
                @Override protected GeoLocationService.SearchResponse doInBackground() {
                    return GeoLocationService.search(query);
                }

                @Override protected void done() {
                    try {
                        GeoLocationService.SearchResponse response = get();
                        searchResults = response.results();
                        results.removeAllItems();
                        for (GeoLocationService.SearchResult result : searchResults) results.addItem(result);
                        if (searchResults.isEmpty()) {
                            status.setText(response.onlineSearchAvailable()
                                    ? "No results found. Check the spelling or try a nearby landmark."
                                    : "Online search unavailable; no matching local landmarks. " + response.error());
                        } else {
                            results.setSelectedIndex(0);
                            centerOnResult();
                            status.setText(searchResults.size() + " location(s) found."
                                    + (response.onlineSearchAvailable() ? "" : " Online search unavailable; showing local matches."));
                        }
                    } catch (Exception error) {
                        status.setText("Search failed: " + error.getMessage());
                    }
                }
            }.execute();
        }

        private void centerOnResult() {
            int index = results.getSelectedIndex();
            if (index < 0 || index >= searchResults.size()) return;
            GeoLocationService.SearchResult result = searchResults.get(index);
            map.setCenter(result.lat(), result.lon(), 16);
            selection = new Selection(result.lat(), result.lon(), result.displayName(), "manual", null);
            map.setSelectedCoordinate(result.lat(), result.lon());
            updateSelectionText();
        }

        private void useApproximateLocation() {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "This uses your internet connection's public IP address to estimate a city-level location. "
                            + "It is not GPS and may be inaccurate. Your IP address is sent to a third-party location service. "
                            + "Use this approximate location?",
                    "Approximate Location and Privacy", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;

            status.setText("Requesting approximate network location...");
            new SwingWorker<GeoLocationService.GeoPoint, Void>() {
                @Override protected GeoLocationService.GeoPoint doInBackground() throws IOException {
                    return GeoLocationService.detectApproximateLocationByIp();
                }

                @Override protected void done() {
                    try {
                        GeoLocationService.GeoPoint point = get();
                        map.setCenter(point.lat(), point.lon(), 12);
                        map.setSelectedCoordinate(point.lat(), point.lon());
                        selection = new Selection(point.lat(), point.lon(),
                                point.label() + " (approximate IP location)", "device", null);
                        updateSelectionText();
                        status.setText("Approximate IP location selected; it is not device GPS.");
                    } catch (Exception error) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status.setText("Location unavailable: " + cause.getMessage());
                    }
                }
            }.execute();
        }

        private void updateSelectionText() {
            if (selection == null) {
                selectionText.setText("No location selected");
                choose.setEnabled(false);
                return;
            }
            selectionText.setText(String.format(Locale.ROOT, "%s — %.6f, %.6f",
                    selection.label(), selection.latitude(), selection.longitude()));
            choose.setEnabled(true);
        }
    }
}
