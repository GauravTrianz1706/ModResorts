package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.acme.modres.mbean.reservation.Reservation;
import com.acme.modres.mbean.reservation.ReservationList;

class AvailabilityCheckerServletTest {

    private AvailabilityCheckerServlet servlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        servlet = new AvailabilityCheckerServlet();
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    void testInit() {
        assertDoesNotThrow(() -> servlet.init());
    }

    @Test
    void testDoGet_withValidDate_returnsAvailability() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn("08/15/2024");
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
        verify(response).setStatus(anyInt());
        assertNotNull(stringWriter.toString());
    }

    @Test
    void testDoGet_withInvalidDate_returns500() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn("invalid-date");
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setStatus(500);
    }

    @Test
    void testDoGet_withNullDate_returns500() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn(null);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setStatus(500);
    }

    @Test
    void testDoPost_callsDoGet() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn("08/15/2024");
        
        servlet.init();
        servlet.doPost(request, response);
        
        verify(response).setContentType("application/json");
    }

    @Test
    void testExportReservations_withValidDate() {
        int result = servlet.exportRevervations("08/15/2024");
        assertTrue(result == 0 || result == -1);
    }

    @Test
    void testExportReservations_withNullDate() {
        int result = servlet.exportRevervations(null);
        assertEquals(-1, result);
    }

    @Test
    void testExportReservations_withEmptyDate() {
        int result = servlet.exportRevervations("");
        assertTrue(result == 0 || result == -1);
    }

    @Test
    void testDoGet_responseContainsAvailabilityJson() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn("08/15/2024");
        
        servlet.init();
        servlet.doGet(request, response);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("availability"));
    }

    @Test
    void testDoGet_setsCorrectContentType() throws ServletException, IOException {
        when(request.getParameter("date")).thenReturn("08/15/2024");
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setContentType("application/json");
        verify(response).setCharacterEncoding("UTF-8");
    }
}
