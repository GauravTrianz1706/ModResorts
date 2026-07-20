package com.acme.modres;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SecondFilterTest {

    private SecondFilter filter;

    @Mock
    private FilterConfig filterConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain chain;

    private StringWriter stringWriter;
    private PrintWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        filter = new SecondFilter();
        stringWriter = new StringWriter();
        writer = new PrintWriter(stringWriter);
        when(response.getWriter()).thenReturn(writer);
    }

    @Test
    void testInit() throws ServletException {
        assertDoesNotThrow(() -> filter.init(filterConfig));
    }

    @Test
    void testDestroy() {
        assertDoesNotThrow(() -> filter.destroy());
    }

    @Test
    void testDoFilter_withRequestContent() throws IOException, ServletException {
        String content = "Hello";
        BufferedReader reader = new BufferedReader(new StringReader(content));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(response).setContentType("text/plain");
        verify(chain).doFilter(request, response);
        writer.flush();
        assertTrue(stringWriter.toString().contains("Hello to our site!"));
    }

    @Test
    void testDoFilter_withEmptyContent() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader(""));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(response).setContentType("text/plain");
        verify(chain).doFilter(request, response);
        writer.flush();
        assertTrue(stringWriter.toString().contains("to our site!"));
    }

    @Test
    void testDoFilter_setsContentType() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader("test"));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(response).setContentType("text/plain");
    }

    @Test
    void testDoFilter_callsChainDoFilter() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader("test"));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(chain).doFilter(request, response);
    }

    @Test
    void testDoFilter_withMultilineContent() throws IOException, ServletException {
        String content = "Line1\nLine2\nLine3";
        BufferedReader reader = new BufferedReader(new StringReader(content));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        writer.flush();
        String output = stringWriter.toString();
        assertTrue(output.contains("to our site!"));
    }

    @Test
    void testDoFilter_appendsToOurSite() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader("Welcome"));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        writer.flush();
        assertTrue(stringWriter.toString().endsWith("to our site! "));
    }

    @Test
    void testDoFilter_readsRequestContent() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader("Content"));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(request).getReader();
    }

    @Test
    void testDoFilter_writesToResponse() throws IOException, ServletException {
        BufferedReader reader = new BufferedReader(new StringReader("Test"));
        when(request.getReader()).thenReturn(reader);
        
        filter.doFilter(request, response, chain);
        
        verify(response).getWriter();
        writer.flush();
        assertFalse(stringWriter.toString().isEmpty());
    }
}
