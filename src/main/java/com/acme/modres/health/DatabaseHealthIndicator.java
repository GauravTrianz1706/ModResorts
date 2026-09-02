package com.acme.modres.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Custom health indicator for database connectivity.
 *
 * Verifies that the application can successfully connect to the configured
 * database (e.g., AWS RDS) by testing the DataSource connection. This ensures
 * that the application's database integration is functional before accepting
 * traffic from container orchestrators (Kubernetes/ECS).
 *
 * Gracefully handles the case when no DataSource is configured (returns UP with
 * a message indicating no DataSource is present). Returns Health.up() when
 * database is accessible, Health.down() on connection failures. Uses try-catch
 * to prevent uncaught exceptions from crashing the health check endpoint.
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

  private static final Logger logger = Logger.getLogger(DatabaseHealthIndicator.class.getName());

  // Spring-managed DataSource injection — compatible with AWS RDS via HikariCP
  // Matches the pattern used in ModResortsCustomerInformation.java
  @Autowired(required = false)
  private DataSource dataSource;

  @Override
  public Health health() {
    // Gracefully handle missing DataSource (not an error condition)
    if (dataSource == null) {
      logger.fine("Database health check: No DataSource configured");
      return Health.up()
          .withDetail("message", "No DataSource configured")
          .build();
    }

    try (Connection connection = dataSource.getConnection()) {
      // Test database connectivity with 5 second timeout
      if (connection.isValid(5)) {
        String databaseInfo = connection.getMetaData().getDatabaseProductName() + " "
            + connection.getMetaData().getDatabaseProductVersion();
        logger.fine("Database health check passed: " + databaseInfo);
        return Health.up()
            .withDetail("database", databaseInfo)
            .build();
      } else {
        logger.warning("Database health check failed: connection not valid");
        return Health.down()
            .withDetail("error", "Connection validation failed")
            .build();
      }
    } catch (SQLException e) {
      logger.severe("Database health check failed: " + e.getMessage());
      return Health.down()
          .withDetail("error", e.getMessage())
          .build();
    } catch (Exception e) {
      logger.severe("Database health check failed with unexpected error: " + e.getMessage());
      return Health.down()
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
