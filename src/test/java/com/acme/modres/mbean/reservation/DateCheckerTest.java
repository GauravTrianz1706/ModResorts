package com.acme.modres.mbean.reservation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class DateCheckerTest {

    private DateChecker dateChecker;

    @Mock
    private ReservationCheckerData data;

    private ReservationList reservationList;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        reservationList = new ReservationList();
        when(data.getReservationList()).thenReturn(reservationList);
    }

    @Test
    void testConstructor() {
        dateChecker = new DateChecker(data);
        assertNotNull(dateChecker);
    }

    @Test
    void testRun_withEmptyReservations() {
        dateChecker = new DateChecker(data);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        assertDoesNotThrow(() -> dateChecker.run());
    }

    @Test
    void testRun_withValidReservations() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("08/01/2024", "08/15/2024"));
        reservationList = new ReservationList(reservations);
        when(data.getReservationList()).thenReturn(reservationList);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker = new DateChecker(data);
        assertDoesNotThrow(() -> dateChecker.run());
    }

    @Test
    void testRun_setsAvailability() {
        dateChecker = new DateChecker(data);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker.run();
        
        verify(data, atLeastOnce()).setAvailablility(anyBoolean());
    }

    @Test
    void testRun_withNullSelectedDate() {
        dateChecker = new DateChecker(data);
        when(data.getSelectedDate()).thenReturn(null);
        
        assertDoesNotThrow(() -> dateChecker.run());
    }

    @Test
    void testRun_withInvalidDateFormat() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("invalid-date", "invalid-date"));
        reservationList = new ReservationList(reservations);
        when(data.getReservationList()).thenReturn(reservationList);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker = new DateChecker(data);
        assertDoesNotThrow(() -> dateChecker.run());
    }

    @Test
    void testRun_withMultipleReservations() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("08/01/2024", "08/10/2024"));
        reservations.add(new Reservation("08/15/2024", "08/20/2024"));
        reservations.add(new Reservation("08/25/2024", "08/30/2024"));
        reservationList = new ReservationList(reservations);
        when(data.getReservationList()).thenReturn(reservationList);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker = new DateChecker(data);
        assertDoesNotThrow(() -> dateChecker.run());
    }

    @Test
    void testRun_callsGetReservationList() {
        dateChecker = new DateChecker(data);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker.run();
        
        verify(data, atLeastOnce()).getReservationList();
    }

    @Test
    void testRun_callsGetSelectedDate() {
        dateChecker = new DateChecker(data);
        when(data.getSelectedDate()).thenReturn(new Date());
        
        dateChecker.run();
        
        verify(data, atLeastOnce()).getSelectedDate();
    }
}
