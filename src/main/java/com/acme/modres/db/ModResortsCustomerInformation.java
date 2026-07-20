package com.acme.modres.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Migrated from EJB 2.x to Spring Boot with HikariCP connection pooling
 * Uses Spring's @Repository annotation for dependency injection
 */
@Repository
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Spring-managed DataSource with HikariCP connection pooling
  @Autowired
  private DataSource dataSource;

  /**
   * Retrieve customer information using connection pooling
   * Uses try-with-resources for automatic resource management
   */
  public ArrayList<String> getCustomerInformation() {
    ArrayList<String> customerInfo = new ArrayList<>();

    // Try-with-resources ensures proper cleanup of database resources
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
         ResultSet rs = stmt.executeQuery()) {

      // Process the results
      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      // Log the exception and return empty list
      System.err.println("Database error while retrieving customer information: " + e.getMessage());
      e.printStackTrace();
    }
    
    return customerInfo;
  }
}
