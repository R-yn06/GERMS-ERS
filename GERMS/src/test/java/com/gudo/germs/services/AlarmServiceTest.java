package com.gudo.germs.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlarmServiceTest {

    @Test
    void alarmServiceSingletonInstanceIsActive() {
        AlarmService service = AlarmService.get();
        assertNotNull(service);
        assertSame(service, AlarmService.get());
    }

    @Test
    void muteToggleRespectsState() {
        AlarmService service = AlarmService.get();
        service.setMuted(true);
        assertTrue(service.isMuted());

        service.setMuted(false);
        assertFalse(service.isMuted());
    }

    @Test
    void triggerAlarmDeduplicatesAndRespectsMute() {
        AlarmService service = AlarmService.get();
        service.setMuted(true);

        // When muted, triggerAlarm should return immediately without playing
        service.triggerAlarm("notif-1", "critical", true, true);
        assertFalse(service.isPlaying());

        // Inactive responder should not trigger audible sound
        service.setMuted(false);
        service.triggerAlarm("notif-2", "critical", false, true);
        assertFalse(service.isPlaying());

        // Audible preference disabled should not trigger audible sound
        service.triggerAlarm("notif-3", "critical", true, false);
        assertFalse(service.isPlaying());
    }
}

