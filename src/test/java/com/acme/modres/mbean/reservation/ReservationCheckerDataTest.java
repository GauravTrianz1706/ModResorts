package com.acme.modres.mbean.reservation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationCheckerDataTest {

    private ReservationCheckerData checkerData;
    private ReservationList reservationList;

    @BeforeEach
    void setUp() {
        reservationList = new ReservationList();
        checkerData = new ReservationCheckerData(reservationList);
    }

    @Test
    void testConstructor() {
        assertNotNull(checkerData);
    }

    @Test
    void testConstructor_setsReservationList() {
        assertEquals(reservationList, checkerData.getReservationList());
    }

    @Test
    void testConstructor_setsAvailableToTrue() {
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testGetReservationList() {
        ReservationList list = checkerData.getReservationList();
        assertNotNull(list);
        assertEquals(reservationList, list);
    }

    @Test
    void testSetSelectedDate_withValidDate() {
        boolean result = checkerData.setSelectedDate("08/15/2024");
        assertTrue(result);
        assertNotNull(checkerData.getSelectedDate());
    }

    @Test
    void testSetSelectedDate_withInvalidDate() {
        boolean result = checkerData.setSelectedDate("invalid-date");
        assertFalse(result);
    }

    @Test
    void testSetSelectedDate_withNullDate() {
        boolean result = checkerData.setSelectedDate(null);
        assertFalse(result);
    }

    @Test
    void testSetSelectedDate_withEmptyDate() {
        boolean result = checkerData.setSelectedDate("");
        assertFalse(result);
    }

    @Test
    void testGetSelectedDate_afterValidSet() {
        checkerData.setSelectedDate("08/15/2024");
        Date date = checkerData.getSelectedDate();
        assertNotNull(date);
    }

    @Test
    void testGetSelectedDate_beforeSet() {
        Date date = checkerData.getSelectedDate();
        assertNull(date);
    }

    @Test
    void testIsAvailible_defaultValue() {
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testSetAvailablility_toFalse() {
        checkerData.setAvailablility(false);
        assertFalse(checkerData.isAvailible());
    }

    @Test
    void testSetAvailablility_toTrue() {
        checkerData.setAvailablility(false);
        checkerData.setAvailablility(true);
        assertTrue(checkerData.isAvailible());
    }

    @Test
    void testSetSelectedDate_withDifferentFormats() {
        boolean result1 = checkerData.setSelectedDate("12/31/2024");
        assertTrue(result1);
        
        boolean result2 = checkerData.setSelectedDate("01/01/2025");
        assertTrue(result2);
    }

    @Test
    void testSetSelectedDate_updatesSelectedDate() {
        checkerData.setSelectedDate("08/15/2024");
        Date firstDate = checkerData.getSelectedDate();
        
        checkerData.setSelectedDate("08/20/2024");
        Date secondDate = checkerData.getSelectedDate();
        
        assertNotEquals(firstDate, secondDate);
    }

    @Test
    void testConstructor_withNullReservationList() {
        ReservationCheckerData data = new ReservationCheckerData(null);
        assertNull(data.getReservationList());
    }

    @Test
    void testSetAvailablility_multipleChanges() {
        checkerData.setAvailablility(false);
        assertFalse(checkerData.isAvailible());
        
        checkerData.setAvailablility(true);
        assertTrue(checkerData.isAvailible());
        
        checkerData.setAvailablility(false);
        assertFalse(checkerData.isAvailible());
    }
}
