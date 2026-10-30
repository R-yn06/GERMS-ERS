package com.gudo.germs.services;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * OBSERVER PATTERN: panels subscribe; the service publishes. Neither knows about the other,
 * so the dashboard and incident list stay in sync without calling each other.
 * Listeners are always invoked on the Event Dispatch Thread.
 */
public final class IncidentBus {
    private final List<Consumer<IncidentEvent>> listeners = new CopyOnWriteArrayList<>();

    /** @return a Runnable that unsubscribes. */
    public Runnable subscribe(Consumer<IncidentEvent> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    void publish(IncidentEvent event) {
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.accept(event)));
    }
}
