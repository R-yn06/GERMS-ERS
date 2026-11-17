package com.gudo.germs.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponderClassificationCodesTest {
    @Test
    void canonicalizesLegacyCodesToDisplayedInitials() {
        assertEquals("EMR", ResponderClassificationCodes.initials("SAF_EMR"));
        assertEquals("ERU", ResponderClassificationCodes.initials("SAF_ERU"));
        assertEquals("EMS", ResponderClassificationCodes.initials("SAF_AMB"));
        assertEquals("DMS", ResponderClassificationCodes.initials("DMS_SAR"));
    }

    @Test
    void keepsCurrentCodesAndNormalizesUnknownCodes() {
        assertEquals("EMS", ResponderClassificationCodes.canonicalCode("ems"));
        assertEquals("VOL_143L", ResponderClassificationCodes.initials("vol_143l"));
        assertEquals("", ResponderClassificationCodes.initials(null));
    }
}
