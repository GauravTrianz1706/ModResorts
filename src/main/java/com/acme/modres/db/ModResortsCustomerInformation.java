package com.acme.modres.db;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Customer information service migrated from singleton pattern to Spring-managed service
 * with distributed caching for containerized deployment.
 * 
 * Fix for blocker-4: Replaced @Singleton with @Service and added @Cacheable
 * to use Amazon ElastiCache (Redis) for distributed caching across horizontally
 * scaled container instances.
 */
@Service
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Inject DataSource using Spring's dependency injection
  // Configure via application.properties with environment variables:
  // spring.datasource.url=${DB_URL}
  // spring.datasource.username=${DB_USERNAME}
  // spring.datasource.password=${DB_PASSWORD}
  @Autowired(required = false)
  private DataSource dataSource;

  /**
   * Get customer information with distributed caching support.
   * Cache is stored in Amazon ElastiCache (Redis) for consistency
   * across multiple container instances.
   * 
   * Configure Redis connection via environment variables:
   * REDIS_HOST, REDIS_PORT, REDIS_PASSWORD
   */
  @Cacheable(value = "customerInfo", unless = "#result == null || #result.isEmpty()")
  public ArrayList<String> getCustomerInformation() {
    Connection conn = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    ArrayList<String> customerInfo = new ArrayList<>();

    if (dataSource == null) {
      System.err.println("[WARN] DataSource not configured. Returning empty customer info.");
      return customerInfo;
    }

    try {
      // Get a connection from the injected data source
      conn = dataSource.getConnection();
      // Create a prepared statement
      stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
      // Execute the query
      rs = stmt.executeQuery();

      // Process the results
      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      e.printStackTrace();
    } finally {
      // Close the result set, statement, and connection
      try {
        if (rs != null)
          rs.close();
        if (stmt != null)
          stmt.close();
        if (conn != null)
          conn.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return customerInfo;
  }
}
