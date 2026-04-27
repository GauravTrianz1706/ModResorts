package com.acme.modres.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Migrated from EJB 2.x to Spring Boot service with HikariCP connection pooling.
 * This provides cloud-native database connectivity with automatic connection management,
 * connection pooling, and compatibility with Cloud SQL.
 */
@Service
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Spring-managed DataSource with HikariCP connection pooling
  @Autowired(required = false)
  private DataSource dataSource;

  /**
   * Retrieves customer information using try-with-resources for automatic resource management.
   * This prevents resource leaks in cloud containers with strict resource limits.
   */
  public ArrayList<String> getCustomerInformation() {
    ArrayList<String> customerInfo = new ArrayList<>();
    
    if (dataSource == null) {
      System.err.println("DataSource not configured. Please configure Cloud SQL connection.");
      return customerInfo;
    }

    // Use try-with-resources for automatic resource management
    // This ensures connections are properly closed and returned to the pool
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
         ResultSet rs = stmt.executeQuery()) {

      // Process the results
      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      System.err.println("Database error: " + e.getMessage());
      e.printStackTrace();
    }
    
    return customerInfo;
  }
}
