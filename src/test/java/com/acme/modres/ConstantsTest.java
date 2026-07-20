package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConstantsTest {

    @Test
    void testCityConstants() {
        assertEquals("Barcelona", Constants.BARCELONA);
        assertEquals("Cork", Constants.CORK);
        assertEquals("Miami", Constants.MIAMI);
        assertEquals("San_Francisco", Constants.SAN_FRANCISCO);
        assertEquals("Paris", Constants.PARIS);
        assertEquals("Las_Vegas", Constants.LAS_VEGAS);
    }

    @Test
    void testSupportedCitiesArray() {
        assertNotNull(Constants.SUPPORTED_CITIES);
        assertEquals(6, Constants.SUPPORTED_CITIES.length);
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.PARIS));
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.LAS_VEGAS));
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.SAN_FRANCISCO));
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.MIAMI));
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.CORK));
        assertTrue(containsCity(Constants.SUPPORTED_CITIES, Constants.BARCELONA));
    }

    @Test
    void testWeatherFileConstants() {
        assertEquals("barcelona.json", Constants.BACELONA_WEATHER_FILE);
        assertEquals("cork.json", Constants.CORK_WEATHER_FILE);
        assertEquals("nv.json", Constants.LAS_VEGAS_WEATHER_FILE);
        assertEquals("miami.json", Constants.MIAMI_WEATHER_FILE);
        assertEquals("paris.json", Constants.PARIS_WEATHER_FILE);
        assertEquals("sanfran.json", Constants.SAN_FRANCESCO_WEATHER_FILE);
    }

    @Test
    void testWundergroundApiConstants() {
        assertEquals("http://api.wunderground.com/api/", Constants.WUNDERGROUND_API_PREFIX);
        assertEquals("/forecast/geolookup/conditions/q/", Constants.WUNDERGROUND_API_PART);
    }

    @Test
    void testDateFormatConstant() {
        assertEquals("MM/dd/yyyy", Constants.DATA_FORMAT);
    }

    @Test
    void testSupportedCitiesNotEmpty() {
        assertTrue(Constants.SUPPORTED_CITIES.length > 0);
    }

    @Test
    void testAllCitiesInSupportedArray() {
        String[] cities = Constants.SUPPORTED_CITIES;
        for (String city : cities) {
            assertNotNull(city);
            assertFalse(city.isEmpty());
        }
    }

    @Test
    void testWeatherFilesHaveJsonExtension() {
        assertTrue(Constants.BACELONA_WEATHER_FILE.endsWith(".json"));
        assertTrue(Constants.CORK_WEATHER_FILE.endsWith(".json"));
        assertTrue(Constants.LAS_VEGAS_WEATHER_FILE.endsWith(".json"));
        assertTrue(Constants.MIAMI_WEATHER_FILE.endsWith(".json"));
        assertTrue(Constants.PARIS_WEATHER_FILE.endsWith(".json"));
        assertTrue(Constants.SAN_FRANCESCO_WEATHER_FILE.endsWith(".json"));
    }

    @Test
    void testApiPrefixStartsWithHttp() {
        assertTrue(Constants.WUNDERGROUND_API_PREFIX.startsWith("http"));
    }

    private boolean containsCity(String[] cities, String city) {
        for (String c : cities) {
            if (c.equals(city)) {
                return true;
            }
        }
        return false;
    }
}
