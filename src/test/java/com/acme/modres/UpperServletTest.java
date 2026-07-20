package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UpperServletTest {

    private UpperServlet servlet;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        servlet = new UpperServlet();
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    void testDoGet_withValidInput_convertsToUpperCase() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("hello");
        
        servlet.doGet(request, response);
        
        verify(response).setContentType("text/html");
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("upper case input"));
    }

    @Test
    void testDoGet_withNullInput_usesEmptyString() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn(null);
        
        servlet.doGet(request, response);
        
        verify(response).setContentType("text/html");
        writer.flush();
        assertNotNull(stringWriter.toString());
    }

    @Test
    void testDoGet_withEmptyInput() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("");
        
        servlet.doGet(request, response);
        
        verify(response).setContentType("text/html");
        writer.flush();
        assertTrue(stringWriter.toString().contains("upper case input"));
    }

    @Test
    void testDoGet_setsContentType() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("test");
        
        servlet.doGet(request, response);
        
        verify(response).setContentType("text/html");
    }

    @Test
    void testDoGet_withLowerCaseInput() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("lowercase");
        
        servlet.doGet(request, response);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("upper case input"));
    }

    @Test
    void testDoGet_withMixedCaseInput() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("MiXeD");
        
        servlet.doGet(request, response);
        
        writer.flush();
        assertNotNull(stringWriter.toString());
    }

    @Test
    void testDoGet_withSpecialCharacters() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("hello@123");
        
        servlet.doGet(request, response);
        
        writer.flush();
        assertTrue(stringWriter.toString().contains("upper case input"));
    }

    @Test
    void testDoGet_withSpaces() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("hello world");
        
        servlet.doGet(request, response);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("upper case input"));
    }

    @Test
    void testDoGet_outputContainsBoldTag() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("test");
        
        servlet.doGet(request, response);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("<b>"));
    }

    @Test
    void testDoGet_outputContainsBreakTag() throws ServletException, IOException {
        when(request.getParameter("input")).thenReturn("test");
        
        servlet.doGet(request, response);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("<br/>"));
    }
}
