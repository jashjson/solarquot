package com.solarquote.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckTest {

    @Test
    void readsTagFromReleaseJson() {
        String json = "{\"url\":\"x\",\"html_url\":\"y\",\"tag_name\": \"v1.2.0\",\"name\":\"SolarQuote v1.2.0\"}";
        assertEquals("1.2.0", UpdateCheck.parseTag(json));
        assertNull(UpdateCheck.parseTag("{\"message\":\"Not Found\"}"));
        assertNull(UpdateCheck.parseTag(null));
    }

    @Test
    void comparesVersionsNumerically() {
        assertTrue(UpdateCheck.isNewer("1.0.1", "1.0.0"));
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.0"));
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"));
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateCheck.isNewer("1.2", "1.2.0"));
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.1"));
    }

    @Test
    void skipsCheckOutsideInstalledApp() {
        // No jpackage launcher in tests, so there is no installed version to compare against
        assertNull(UpdateCheck.currentVersion());
        assertNull(UpdateCheck.newerVersion());
    }
}
