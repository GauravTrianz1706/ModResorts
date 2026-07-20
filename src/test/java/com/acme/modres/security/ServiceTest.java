package com.acme.modres.security;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceTest {

    private Service service;

    @BeforeEach
    void setUp() {
        service = new Service();
    }

    @Test
    void testConstructor() {
        assertNotNull(service);
    }

    @Test
    void testOperation() {
        assertDoesNotThrow(() -> {
            service.operation();
        });
    }

    @Test
    void testOperation_executesSuccessfully() {
        service.operation();
        // If no exception is thrown, the test passes
        assertTrue(true);
    }

    @Test
    void testOperation_canBeCalledMultipleTimes() {
        assertDoesNotThrow(() -> {
            service.operation();
            service.operation();
            service.operation();
        });
    }

    @Test
    void testOperationConstant() {
        assertEquals("my-operation", Service.OPERATION);
    }

    @Test
    void testOperationConstant_isNotNull() {
        assertNotNull(Service.OPERATION);
    }

    @Test
    void testOperationConstant_isNotEmpty() {
        assertFalse(Service.OPERATION.isEmpty());
    }

    @Test
    void testMultipleServiceInstances() {
        Service service1 = new Service();
        Service service2 = new Service();
        
        assertNotNull(service1);
        assertNotNull(service2);
        assertNotSame(service1, service2);
    }

    @Test
    void testOperation_doesNotReturnValue() {
        // operation() is void, so we just verify it doesn't throw
        assertDoesNotThrow(() -> service.operation());
    }
}
