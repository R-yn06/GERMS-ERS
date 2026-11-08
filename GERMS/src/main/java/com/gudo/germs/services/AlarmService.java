package com.gudo.germs.services;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Emergency Alarm System for GERMS.
 * Synthesizes severity-based audible tones (Low, Moderate, High, Critical)
 * using the standard javax.sound.sampled pipeline without external file dependencies.
 * Supports repetition control, deduplication, active status checks, and instant dismissal/acknowledgement.
 */
public final class AlarmService {
    private static AlarmService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "GERMS-AlarmThread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean playing = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final Set<String> playedNotifications = Collections.synchronizedSet(new HashSet<>());
    private volatile SourceDataLine currentLine;

    private AlarmService() {}

    public static synchronized AlarmService get() {
        if (instance == null) {
            instance = new AlarmService();
        }
        return instance;
    }

    public boolean isMuted() {
        return muted.get();
    }

    public void setMuted(boolean mute) {
        this.muted.set(mute);
        if (mute) {
            stopAlarm();
        }
    }

    public boolean isPlaying() {
        return playing.get();
    }

    /**
     * Stop active audible alarm playback immediately (dismissal / acknowledgement).
     */
    public void stopAlarm() {
        playing.set(false);
        if (currentLine != null) {
            try {
                currentLine.stop();
                currentLine.flush();
                currentLine.close();
            } catch (Exception ignored) {}
            currentLine = null;
        }
    }

    /**
     * Play an alarm corresponding to incident severity if eligible and not muted.
     * Prevents duplicate playback for the same notification ID.
     */
    public void triggerAlarm(String notificationId, String severity, boolean isResponderActive, boolean audiblePreference) {
        if (!isResponderActive || !audiblePreference || muted.get()) {
            return; // Inactive responders and users with audible disabled receive notifications visually only
        }
        if (notificationId != null && !notificationId.isBlank()) {
            if (playedNotifications.contains(notificationId)) {
                return; // Duplicate prevention
            }
            playedNotifications.add(notificationId);
        }

        stopAlarm();
        playing.set(true);

        executor.submit(() -> {
            try {
                String sev = (severity == null ? "moderate" : severity).toLowerCase(Locale.ROOT);
                switch (sev) {
                    case "critical" -> playCriticalAlarm();
                    case "high" -> playHighAlarm();
                    case "low" -> playLowChime();
                    default -> playModerateAlert();
                }
            } catch (Exception e) {
                System.err.println("Alarm playback error: " + e.getMessage());
            } finally {
                playing.set(false);
            }
        });
    }

    // --- Sound Synthesis Engines ---

    private void playLowChime() throws LineUnavailableException {
        // Single gentle 880Hz chime with smooth exponential decay (approx 350ms)
        byte[] p = generateTone(880, 350, 0.4f, true);
        playRawPcm(p, 44100);
    }

    private void playModerateAlert() throws LineUnavailableException {
        // Two ascending alert chimes: 659Hz (E5) -> 880Hz (A5)
        byte[] tone1 = generateTone(659, 180, 0.5f, true);
        byte[] silence = new byte[800];
        byte[] tone2 = generateTone(880, 320, 0.6f, true);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(tone1);
        out.writeBytes(silence);
        out.writeBytes(tone2);
        playRawPcm(out.toByteArray(), 44100);
    }

    private void playHighAlarm() throws LineUnavailableException {
        // Urgent 3-pulse cadence alternating between 784Hz (G5) and 1046Hz (C6)
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < 3 && playing.get(); i++) {
            out.writeBytes(generateTone(784, 150, 0.7f, false));
            out.writeBytes(generateTone(1046, 180, 0.8f, false));
            out.writeBytes(new byte[1200]);
        }
        playRawPcm(out.toByteArray(), 44100);
    }

    private void playCriticalAlarm() throws LineUnavailableException {
        // High priority warbling siren repeated up to 5 times unless acknowledged
        for (int cycle = 0; cycle < 5 && playing.get(); cycle++) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (int f = 900; f <= 1350; f += 45) {
                out.writeBytes(generateTone(f, 25, 0.85f, false));
            }
            for (int f = 1350; f >= 900; f -= 45) {
                out.writeBytes(generateTone(f, 25, 0.85f, false));
            }
            out.writeBytes(new byte[2000]);
            playRawPcm(out.toByteArray(), 44100);
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private byte[] generateTone(int freqHz, int durationMs, float maxVolume, boolean decay) {
        int sampleRate = 44100;
        int numSamples = (sampleRate * durationMs) / 1000;
        byte[] output = new byte[numSamples * 2]; // 16-bit mono

        double angleStep = 2.0 * Math.PI * freqHz / sampleRate;
        double currentAngle = 0.0;

        for (int i = 0; i < numSamples; i++) {
            double amplitude = Math.sin(currentAngle);
            if (decay) {
                amplitude *= (1.0 - (double) i / numSamples); // smooth linear/exponential-like decay
            }
            short sample = (short) (amplitude * maxVolume * 32767);
            output[2 * i] = (byte) (sample & 0xFF);
            output[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
            currentAngle += angleStep;
        }
        return output;
    }

    private synchronized void playRawPcm(byte[] pcmData, int sampleRate) throws LineUnavailableException {
        if (!playing.get() || muted.get()) return;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        SourceDataLine line = AudioSystem.getSourceDataLine(format);
        currentLine = line;
        line.open(format);
        line.start();

        int offset = 0;
        int chunkSize = 2048;
        while (offset < pcmData.length && playing.get() && !muted.get()) {
            int toWrite = Math.min(chunkSize, pcmData.length - offset);
            line.write(pcmData, offset, toWrite);
            offset += toWrite;
        }
        line.drain();
        line.stop();
        line.close();
        currentLine = null;
    }
}

