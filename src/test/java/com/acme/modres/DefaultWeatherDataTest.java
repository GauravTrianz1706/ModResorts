package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class DefaultWeatherDataTest {

    @Test
    void testConstructor_withValidCity() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.PARIS);
        assertEquals(Constants.PARIS, data.getCity());
    }

    @Test
    void testConstructor_withNullCity_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            new DefaultWeatherData(null);
        });
    }

    @Test
    void testConstructor_withInvalidCity_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            new DefaultWeatherData("InvalidCity");
        });
    }

    @Test
    void testGetCity_returnsCorrectCity() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.MIAMI);
        assertEquals(Constants.MIAMI, data.getCity());
    }

    @Test
    void testGetDefaultWeatherData_forParis() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.PARIS);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testGetDefaultWeatherData_forLasVegas() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.LAS_VEGAS);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testGetDefaultWeatherData_forSanFrancisco() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.SAN_FRANCISCO);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testGetDefaultWeatherData_forMiami() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.MIAMI);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testGetDefaultWeatherData_forCork() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.CORK);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testGetDefaultWeatherData_forBarcelona() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.BARCELONA);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
    }

    @Test
    void testConstructor_withAllSupportedCities() {
        for (String city : Constants.SUPPORTED_CITIES) {
            assertDoesNotThrow(() -> {
                DefaultWeatherData data = new DefaultWeatherData(city);
                assertEquals(city, data.getCity());
            });
        }
    }

    @Test
    void testConstructor_withEmptyCity_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            new DefaultWeatherData("");
        });
    }

    @Test
    void testGetDefaultWeatherData_returnsNonEmptyString() throws IOException {
        DefaultWeatherData data = new DefaultWeatherData(Constants.PARIS);
        String weatherData = data.getDefaultWeatherData();
        assertNotNull(weatherData);
        assertFalse(weatherData.isEmpty());
    }
}
