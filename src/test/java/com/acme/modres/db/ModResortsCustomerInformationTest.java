package com.acme.modres.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ModResortsCustomerInformationTest {

    private ModResortsCustomerInformation customerInfo;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);
        customerInfo = new ModResortsCustomerInformation();
    }

    @Test
    void testGetCustomerInformation_withNullDataSource_returnsEmptyList() {
        ArrayList<String> result = customerInfo.getCustomerInformation();
        
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetCustomerInformation_returnsArrayList() {
        ArrayList<String> result = customerInfo.getCustomerInformation();
        
        assertNotNull(result);
        assertTrue(result instanceof ArrayList);
    }

    @Test
    void testGetCustomerInformation_handlesNullDataSource() {
        assertDoesNotThrow(() -> {
            customerInfo.getCustomerInformation();
        });
    }

    @Test
    void testGetCustomerInformation_returnsNonNullList() {
        ArrayList<String> result = customerInfo.getCustomerInformation();
        
        assertNotNull(result);
    }

    @Test
    void testGetCustomerInformation_emptyResultWhenNoDataSource() {
        ArrayList<String> result = customerInfo.getCustomerInformation();
        
        assertEquals(0, result.size());
    }
}
