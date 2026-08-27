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
 * Customer information service migrated from EJB 2.x to Spring Boot.
 *
 * Replaced EJB 2.x annotations (@Singleton, @Startup from javax.ejb) with
 * Spring Boot @Service stereotype annotation. The DataSource is injected via
 * Spring's @Autowired instead of EJB @Resource, enabling use of AWS RDS or
 * any Spring-managed DataSource (e.g., HikariCP connection pool) without
 * requiring a heavyweight EJB container
 * (fixes cr-java-0085 EJB 2.x Usage).
 */
@Service
public class ModResortsCustomerInformation {
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // Spring-managed DataSource injection — compatible with AWS RDS via HikariCP
  // or any JDBC DataSource configured in application.properties
  @Autowired(required = false)
  private DataSource dataSource;

  public ArrayList<String> getCustomerInformation() {
    ArrayList<String> customerInfo = new ArrayList<>();

    if (dataSource == null) {
      return customerInfo;
    }

    // Use try-with-resources for automatic resource management
    try (Connection conn = dataSource.getConnection();
        PreparedStatement stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
        ResultSet rs = stmt.executeQuery()) {

      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
    return customerInfo;
  }
}
