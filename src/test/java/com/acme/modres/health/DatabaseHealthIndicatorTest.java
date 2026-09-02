package com.acme.modres.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DatabaseHealthIndicator.
 *
 * Tests verify that the health indicator correctly returns UP status when
 * database is accessible, UP status when DataSource is null (not configured),
 * and DOWN status when SQLException occurs. Uses Mockito to mock DataSource and
 * avoid external dependencies.
 */
public class DatabaseHealthIndicatorTest {

  private DatabaseHealthIndicator healthIndicator;

  @BeforeEach
  public void setUp() {
    healthIndicator = new DatabaseHealthIndicator();
  }

  @Test
  public void testHealthUp_WhenDataSourceIsNull() {
    // DataSource is not set (null by default)
    Health health = healthIndicator.health();

    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertEquals("No DataSource configured", health.getDetails().get("message"));
  }

  @Test
  public void testHealthUp_WhenDatabaseIsAccessible() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);
    DatabaseMetaData mockMetaData = mock(DatabaseMetaData.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.isValid(5)).thenReturn(true);
    when(mockConnection.getMetaData()).thenReturn(mockMetaData);
    when(mockMetaData.getDatabaseProductName()).thenReturn("PostgreSQL");
    when(mockMetaData.getDatabaseProductVersion()).thenReturn("13.4");

    // Inject mock DataSource using reflection
    ReflectionTestUtils.setField(healthIndicator, "dataSource", mockDataSource);

    Health health = healthIndicator.health();

    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertNotNull(health.getDetails().get("database"));
    assertEquals("PostgreSQL 13.4", health.getDetails().get("database"));
  }

  @Test
  public void testHealthDown_WhenConnectionIsNotValid() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);
    Connection mockConnection = mock(Connection.class);

    when(mockDataSource.getConnection()).thenReturn(mockConnection);
    when(mockConnection.isValid(5)).thenReturn(false);

    // Inject mock DataSource using reflection
    ReflectionTestUtils.setField(healthIndicator, "dataSource", mockDataSource);

    Health health = healthIndicator.health();

    assertNotNull(health);
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Connection validation failed", health.getDetails().get("error"));
  }

  @Test
  public void testHealthDown_WhenSQLExceptionOccurs() throws SQLException {
    DataSource mockDataSource = mock(DataSource.class);

    when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

    // Inject mock DataSource using reflection
    ReflectionTestUtils.setField(healthIndicator, "dataSource", mockDataSource);

    Health health = healthIndicator.health();

    assertNotNull(health);
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("Connection refused", health.getDetails().get("error"));
  }
}
