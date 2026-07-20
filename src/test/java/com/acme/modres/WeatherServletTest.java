package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.acme.modres.db.ModResortsCustomerInformation;

class WeatherServletTest {

    private WeatherServlet servlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream outputStream;

    @Mock
    private ModResortsCustomerInformation customerInfo;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        servlet = new WeatherServlet();
        when(response.getOutputStream()).thenReturn(outputStream);
    }

    @Test
    void testInit() {
        assertDoesNotThrow(() -> servlet.init());
    }

    @Test
    void testDestroy() {
        servlet.init();
        assertDoesNotThrow(() -> servlet.destroy());
    }

    @Test
    void testDoGet_withValidCity() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.PARIS);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setContentType("application/json");
    }

    @Test
    void testDoGet_withParis() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.PARIS);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_withLasVegas() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.LAS_VEGAS);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_withSanFrancisco() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.SAN_FRANCISCO);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_withMiami() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.MIAMI);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_withCork() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.CORK);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_withBarcelona() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.BARCELONA);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoPost_callsDoGet() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.PARIS);
        
        servlet.init();
        servlet.doPost(request, response);
        
        verify(request).getParameter("selectedCity");
    }

    @Test
    void testDoGet_setsJsonContentType() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(Constants.PARIS);
        
        servlet.init();
        servlet.doGet(request, response);
        
        verify(response).setContentType("application/json");
    }

    @Test
    void testDoGet_withNullCity() throws ServletException, IOException {
        when(request.getParameter("selectedCity")).thenReturn(null);
        
        servlet.init();
        
        assertThrows(ServletException.class, () -> {
            servlet.doGet(request, response);
        });
    }
}
