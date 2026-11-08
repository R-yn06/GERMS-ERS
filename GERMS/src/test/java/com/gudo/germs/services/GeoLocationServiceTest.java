package com.gudo.germs.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoLocationServiceTest {
    @Test
    void acceptsValidLatitudeAndLongitudeBounds() {
        assertTrue(GeoLocationService.validCoordinates(0, 0));
        assertTrue(GeoLocationService.validCoordinates(-90, -180));
        assertTrue(GeoLocationService.validCoordinates(90, 180));
    }

    @Test
    void rejectsOutOfRangeAndNonFiniteCoordinates() {
        assertFalse(GeoLocationService.validCoordinates(-90.01, 0));
        assertFalse(GeoLocationService.validCoordinates(0, 180.01));
        assertFalse(GeoLocationService.validCoordinates(Double.NaN, 0));
        assertFalse(GeoLocationService.validCoordinates(0, Double.POSITIVE_INFINITY));
    }
}
