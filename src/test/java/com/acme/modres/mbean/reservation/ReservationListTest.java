package com.acme.modres.mbean.reservation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationListTest {

    private ReservationList reservationList;

    @BeforeEach
    void setUp() {
        reservationList = new ReservationList();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(reservationList);
        assertNotNull(reservationList.getReservations());
    }

    @Test
    void testParameterizedConstructor() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("08/01/2024", "08/15/2024"));
        
        ReservationList list = new ReservationList(reservations);
        
        assertNotNull(list);
        assertEquals(1, list.getReservations().size());
    }

    @Test
    void testGetReservations_returnsNonNull() {
        List<Reservation> reservations = reservationList.getReservations();
        assertNotNull(reservations);
    }

    @Test
    void testGetReservations_initiallyEmpty() {
        List<Reservation> reservations = reservationList.getReservations();
        assertTrue(reservations.isEmpty());
    }

    @Test
    void testAdd_singleReservation() {
        Reservation reservation = new Reservation("08/01/2024", "08/15/2024");
        reservationList.add(reservation);
        
        assertEquals(1, reservationList.getReservations().size());
    }

    @Test
    void testAdd_multipleReservations() {
        reservationList.add(new Reservation("08/01/2024", "08/10/2024"));
        reservationList.add(new Reservation("08/15/2024", "08/20/2024"));
        reservationList.add(new Reservation("08/25/2024", "08/30/2024"));
        
        assertEquals(3, reservationList.getReservations().size());
    }

    @Test
    void testAdd_preservesOrder() {
        Reservation res1 = new Reservation("08/01/2024", "08/10/2024");
        Reservation res2 = new Reservation("08/15/2024", "08/20/2024");
        
        reservationList.add(res1);
        reservationList.add(res2);
        
        List<Reservation> reservations = reservationList.getReservations();
        assertEquals("08/01/2024", reservations.get(0).getFromDate());
        assertEquals("08/15/2024", reservations.get(1).getFromDate());
    }

    @Test
    void testAdd_withNullReservation() {
        reservationList.add(null);
        
        assertEquals(1, reservationList.getReservations().size());
        assertNull(reservationList.getReservations().get(0));
    }

    @Test
    void testParameterizedConstructor_withEmptyList() {
        List<Reservation> reservations = new ArrayList<>();
        ReservationList list = new ReservationList(reservations);
        
        assertTrue(list.getReservations().isEmpty());
    }

    @Test
    void testParameterizedConstructor_withMultipleReservations() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("08/01/2024", "08/10/2024"));
        reservations.add(new Reservation("08/15/2024", "08/20/2024"));
        
        ReservationList list = new ReservationList(reservations);
        
        assertEquals(2, list.getReservations().size());
    }

    @Test
    void testGetReservations_returnsModifiableList() {
        List<Reservation> reservations = reservationList.getReservations();
        reservations.add(new Reservation("08/01/2024", "08/10/2024"));
        
        assertEquals(1, reservationList.getReservations().size());
    }

    @Test
    void testAdd_afterParameterizedConstructor() {
        List<Reservation> reservations = new ArrayList<>();
        reservations.add(new Reservation("08/01/2024", "08/10/2024"));
        
        ReservationList list = new ReservationList(reservations);
        list.add(new Reservation("08/15/2024", "08/20/2024"));
        
        assertEquals(2, list.getReservations().size());
    }

    @Test
    void testParameterizedConstructor_withNullList() {
        ReservationList list = new ReservationList(null);
        assertNull(list.getReservations());
    }
}
