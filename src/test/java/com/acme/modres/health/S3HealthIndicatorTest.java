package com.acme.modres.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for S3HealthIndicator.
 *
 * Tests verify that the health indicator correctly returns UP status when S3 is
 * accessible and DOWN status when S3Exception occurs. Uses Mockito to mock
 * S3Client and avoid external dependencies.
 */
public class S3HealthIndicatorTest {

  private S3HealthIndicator healthIndicator;

  @BeforeEach
  public void setUp() {
    healthIndicator = new S3HealthIndicator();
  }

  @Test
  public void testHealthUp_WhenS3IsAccessible() {
    S3Client mockS3Client = mock(S3Client.class);
    S3ClientBuilder mockBuilder = mock(S3ClientBuilder.class);
    ListObjectsV2Response mockResponse = ListObjectsV2Response.builder().build();

    when(mockBuilder.build()).thenReturn(mockS3Client);
    when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(mockResponse);

    try (MockedStatic<S3Client> mockedStatic = mockStatic(S3Client.class)) {
      mockedStatic.when(S3Client::builder).thenReturn(mockBuilder);

      Health health = healthIndicator.health();

      assertNotNull(health);
      assertEquals(Status.UP, health.getStatus());
      assertNotNull(health.getDetails().get("bucket"));
      assertEquals("S3 bucket accessible", health.getDetails().get("message"));
    }
  }

  @Test
  public void testHealthDown_WhenS3ThrowsException() {
    S3Client mockS3Client = mock(S3Client.class);
    S3ClientBuilder mockBuilder = mock(S3ClientBuilder.class);

    AwsErrorDetails errorDetails = AwsErrorDetails.builder()
        .errorMessage("Access Denied")
        .build();
    S3Exception s3Exception = (S3Exception) S3Exception.builder()
        .awsErrorDetails(errorDetails)
        .build();

    when(mockBuilder.build()).thenReturn(mockS3Client);
    when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(s3Exception);

    try (MockedStatic<S3Client> mockedStatic = mockStatic(S3Client.class)) {
      mockedStatic.when(S3Client::builder).thenReturn(mockBuilder);

      Health health = healthIndicator.health();

      assertNotNull(health);
      assertEquals(Status.DOWN, health.getStatus());
      assertNotNull(health.getDetails().get("bucket"));
      assertEquals("Access Denied", health.getDetails().get("error"));
    }
  }
}
