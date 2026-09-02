package com.acme.modres.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsRequest;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

import java.util.logging.Logger;

/**
 * Custom health indicator for AWS Secrets Manager connectivity.
 *
 * Verifies that the application can successfully connect to AWS Secrets Manager
 * by attempting a ListSecrets operation. This ensures that the application can
 * retrieve externalized secrets (e.g., API keys, database credentials) before
 * accepting traffic from container orchestrators (Kubernetes/ECS).
 *
 * Returns Health.up() when Secrets Manager is accessible, Health.down() on
 * connection failures. Does not retrieve or expose actual secret values. Uses
 * try-catch to prevent uncaught exceptions from crashing the health check
 * endpoint.
 */
@Component
public class SecretsManagerHealthIndicator implements HealthIndicator {

  private static final Logger logger = Logger.getLogger(SecretsManagerHealthIndicator.class.getName());

  // AWS region read from environment variable, matching pattern in WeatherServlet.java
  private static final String AWS_REGION = System.getenv("AWS_REGION") != null
      ? System.getenv("AWS_REGION")
      : "us-east-1";

  @Override
  public Health health() {
    try (SecretsManagerClient secretsClient = SecretsManagerClient.builder()
        .region(Region.of(AWS_REGION))
        .build()) {

      // Attempt ListSecrets to verify Secrets Manager connectivity
      // Does not retrieve actual secret values
      ListSecretsRequest listRequest = ListSecretsRequest.builder()
          .maxResults(1)
          .build();

      secretsClient.listSecrets(listRequest);

      logger.fine("Secrets Manager health check passed");
      return Health.up()
          .withDetail("message", "Secrets Manager accessible")
          .build();

    } catch (SecretsManagerException e) {
      logger.severe("Secrets Manager health check failed: " + e.awsErrorDetails().errorMessage());
      return Health.down()
          .withDetail("error", e.awsErrorDetails().errorMessage())
          .build();
    } catch (Exception e) {
      logger.severe("Secrets Manager health check failed with unexpected error: " + e.getMessage());
      return Health.down()
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
