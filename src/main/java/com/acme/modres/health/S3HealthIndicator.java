package com.acme.modres.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.logging.Logger;

/**
 * Custom health indicator for Amazon S3 connectivity.
 *
 * Verifies that the application can successfully connect to the configured S3
 * bucket by attempting a ListObjectsV2 operation. This ensures that the
 * application's S3 integration is functional before accepting traffic from
 * container orchestrators (Kubernetes/ECS).
 *
 * Returns Health.up() when S3 is accessible, Health.down() on connection
 * failures. Uses try-catch to prevent uncaught exceptions from crashing the
 * health check endpoint.
 */
@Component
public class S3HealthIndicator implements HealthIndicator {

  private static final Logger logger = Logger.getLogger(S3HealthIndicator.class.getName());

  // S3 bucket name read from environment variable for cloud-native configuration
  // Matches the pattern used in IOUtils.java
  private static final String S3_BUCKET_NAME = System.getenv("S3_BUCKET_NAME") != null
      ? System.getenv("S3_BUCKET_NAME")
      : "modresorts-data";

  @Override
  public Health health() {
    try (S3Client s3Client = S3Client.builder().build()) {
      // Attempt ListObjectsV2 to verify S3 connectivity
      ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
          .bucket(S3_BUCKET_NAME)
          .maxKeys(1)
          .build();

      s3Client.listObjectsV2(listRequest);

      logger.fine("S3 health check passed for bucket: " + S3_BUCKET_NAME);
      return Health.up()
          .withDetail("bucket", S3_BUCKET_NAME)
          .withDetail("message", "S3 bucket accessible")
          .build();

    } catch (S3Exception e) {
      logger.severe("S3 health check failed for bucket " + S3_BUCKET_NAME + ": "
          + e.awsErrorDetails().errorMessage());
      return Health.down()
          .withDetail("bucket", S3_BUCKET_NAME)
          .withDetail("error", e.awsErrorDetails().errorMessage())
          .build();
    } catch (Exception e) {
      logger.severe("S3 health check failed with unexpected error: " + e.getMessage());
      return Health.down()
          .withDetail("bucket", S3_BUCKET_NAME)
          .withDetail("error", e.getMessage())
          .build();
    }
  }
}
