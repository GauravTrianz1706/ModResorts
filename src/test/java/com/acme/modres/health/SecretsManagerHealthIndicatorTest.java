package com.acme.modres.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SecretsManagerHealthIndicator.
 *
 * Tests verify that the health indicator correctly returns UP status when
 * Secrets Manager is accessible and DOWN status when SecretsManagerException
 * occurs. Uses Mockito to mock SecretsManagerClient and avoid external
 * dependencies.
 */
public class SecretsManagerHealthIndicatorTest {

  private SecretsManagerHealthIndicator healthIndicator;

  @BeforeEach
  public void setUp() {
    healthIndicator = new SecretsManagerHealthIndicator();
  }

  @Test
  public void testHealthUp_WhenSecretsManagerIsAccessible() {
    SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
    SecretsManagerClientBuilder mockBuilder = mock(SecretsManagerClientBuilder.class);
    ListSecretsResponse mockResponse = ListSecretsResponse.builder().build();

    when(mockBuilder.region(any())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenReturn(mockClient);
    when(mockClient.listSecrets(any(ListSecretsRequest.class))).thenReturn(mockResponse);

    try (MockedStatic<SecretsManagerClient> mockedStatic = mockStatic(SecretsManagerClient.class)) {
      mockedStatic.when(SecretsManagerClient::builder).thenReturn(mockBuilder);

      Health health = healthIndicator.health();

      assertNotNull(health);
      assertEquals(Status.UP, health.getStatus());
      assertEquals("Secrets Manager accessible", health.getDetails().get("message"));
    }
  }

  @Test
  public void testHealthDown_WhenSecretsManagerThrowsException() {
    SecretsManagerClient mockClient = mock(SecretsManagerClient.class);
    SecretsManagerClientBuilder mockBuilder = mock(SecretsManagerClientBuilder.class);

    AwsErrorDetails errorDetails = AwsErrorDetails.builder()
        .errorMessage("Access Denied")
        .build();
    SecretsManagerException exception = (SecretsManagerException) SecretsManagerException.builder()
        .awsErrorDetails(errorDetails)
        .build();

    when(mockBuilder.region(any())).thenReturn(mockBuilder);
    when(mockBuilder.build()).thenReturn(mockClient);
    when(mockClient.listSecrets(any(ListSecretsRequest.class))).thenThrow(exception);

    try (MockedStatic<SecretsManagerClient> mockedStatic = mockStatic(SecretsManagerClient.class)) {
      mockedStatic.when(SecretsManagerClient::builder).thenReturn(mockBuilder);

      Health health = healthIndicator.health();

      assertNotNull(health);
      assertEquals(Status.DOWN, health.getStatus());
      assertEquals("Access Denied", health.getDetails().get("error"));
    }
  }
}
