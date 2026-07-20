package com.acme.modres.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.logging.Logger;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ExceptionHandlerTest {

    @Mock
    private Logger logger;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHandleException_withNullException_throwsServletException() {
        String errorMsg = "Test error message";
        
        ServletException exception = assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(null, errorMsg, logger);
        });
        
        assertEquals(errorMsg, exception.getMessage());
    }

    @Test
    void testHandleException_withException_throwsServletException() {
        Exception cause = new RuntimeException("Cause exception");
        String errorMsg = "Test error message";
        
        ServletException exception = assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(cause, errorMsg, logger);
        });
        
        assertEquals(errorMsg, exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testHandleException_withNullException_logsError() {
        String errorMsg = "Test error message";
        
        assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(null, errorMsg, logger);
        });
    }

    @Test
    void testHandleException_withException_logsError() {
        Exception cause = new RuntimeException("Cause");
        String errorMsg = "Error message";
        
        assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(cause, errorMsg, logger);
        });
    }

    @Test
    void testHandleException_preservesErrorMessage() {
        String errorMsg = "Custom error message";
        
        ServletException exception = assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(null, errorMsg, logger);
        });
        
        assertTrue(exception.getMessage().contains(errorMsg));
    }

    @Test
    void testHandleException_preservesCause() {
        Exception cause = new IllegalArgumentException("Invalid argument");
        String errorMsg = "Error occurred";
        
        ServletException exception = assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(cause, errorMsg, logger);
        });
        
        assertNotNull(exception.getCause());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testHandleException_withEmptyMessage() {
        String errorMsg = "";
        
        assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(null, errorMsg, logger);
        });
    }

    @Test
    void testHandleException_alwaysThrowsServletException() {
        assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(null, "message", logger);
        });
        
        assertThrows(ServletException.class, () -> {
            ExceptionHandler.handleException(new Exception(), "message", logger);
        });
    }
}
