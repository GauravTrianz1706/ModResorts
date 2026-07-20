package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class LogoutServletTest {

    private LogoutServlet servlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HttpSession session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        servlet = new LogoutServlet();
    }

    @Test
    void testDoGet_withValidSession_invalidatesSession() throws IOException {
        when(request.getSession(false)).thenReturn(session);
        
        servlet.doGet(request, response);
        
        verify(session).invalidate();
        verify(response).sendRedirect("login.jsp");
    }

    @Test
    void testDoGet_withNullSession_redirectsToLogin() throws IOException {
        when(request.getSession(false)).thenReturn(null);
        
        servlet.doGet(request, response);
        
        verify(response).sendRedirect("login.jsp");
    }

    @Test
    void testDoGet_redirectsToLoginPage() throws IOException {
        when(request.getSession(false)).thenReturn(session);
        
        servlet.doGet(request, response);
        
        verify(response).sendRedirect("login.jsp");
    }

    @Test
    void testDoGet_handlesSessionInvalidationException() throws IOException {
        when(request.getSession(false)).thenReturn(session);
        doThrow(new IllegalStateException("Session already invalidated")).when(session).invalidate();
        
        servlet.doGet(request, response);
        
        verify(response).sendRedirect("login.jsp");
    }

    @Test
    void testDoGet_callsGetSessionWithFalse() throws IOException {
        when(request.getSession(false)).thenReturn(session);
        
        servlet.doGet(request, response);
        
        verify(request).getSession(false);
    }

    @Test
    void testDoGet_doesNotCreateNewSession() throws IOException {
        when(request.getSession(false)).thenReturn(null);
        
        servlet.doGet(request, response);
        
        verify(request).getSession(false);
        verify(request, never()).getSession(true);
    }

    @Test
    void testDoGet_alwaysRedirects() throws IOException {
        when(request.getSession(false)).thenReturn(session);
        
        servlet.doGet(request, response);
        
        verify(response, times(1)).sendRedirect(anyString());
    }
}
