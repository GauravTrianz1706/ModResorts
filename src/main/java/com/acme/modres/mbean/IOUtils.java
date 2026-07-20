package com.acme.modres.mbean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public final class IOUtils {

  // S3 configuration from environment variables
  private static final String S3_BUCKET_NAME = System.getenv().getOrDefault("S3_BUCKET_NAME", "modresorts-data");
  private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

  /**
   * Read file from classpath resources (cloud-compatible)
   * Falls back to S3 if not found in classpath
   */
  public static InputStream getInputStreamFromPath(String path) {
    InputStream stream = null;
    
    try {
      // First try to load from classpath
      stream = IOUtils.class.getClassLoader().getResourceAsStream(path);
      
      // If not found in classpath, try S3
      if (stream == null) {
        try (S3Client s3Client = S3Client.builder()
            .region(Region.of(AWS_REGION))
            .build()) {
          
          GetObjectRequest getObjectRequest = GetObjectRequest.builder()
              .bucket(S3_BUCKET_NAME)
              .key("config/" + path)
              .build();
          
          // Read S3 object into memory
          byte[] data = s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
          stream = new ByteArrayInputStream(data);
        } catch (Exception e) {
          System.err.println("Failed to load from S3: " + e.getMessage());
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    
    return stream;
  }

  /**
   * Write data to S3 instead of local file system
   */
  public static boolean writeToS3(String key, byte[] data) {
    try (S3Client s3Client = S3Client.builder()
        .region(Region.of(AWS_REGION))
        .build()) {
      
      PutObjectRequest putObjectRequest = PutObjectRequest.builder()
          .bucket(S3_BUCKET_NAME)
          .key(key)
          .build();
      
      s3Client.putObject(putObjectRequest, RequestBody.fromBytes(data));
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  public static OpMetadataList getOpListFromConfig() {
    try (InputStream stream = getInputStreamFromPath("ops.json")) {
      if (stream == null) {
        System.err.println("ops.json not found in classpath or S3");
        return new OpMetadataList(); // empty default
      }
      
      // Read stream into byte array for JsonInputStream
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[1024];
      int nRead;
      while ((nRead = stream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }
      
      // Create temporary input stream for JsonInputStream
      try (ByteArrayInputStream bais = new ByteArrayInputStream(buffer.toByteArray());
           JsonInputStream is = new JsonInputStream(bais)) {
        OpMetadataList opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
        return opList != null ? opList : new OpMetadataList();
      }
    } catch (IOException e) {
      e.printStackTrace();
      return new OpMetadataList();
    }
  }

  public static ReservationList getReservationListFromConfig() {
    try (InputStream stream = getInputStreamFromPath("reservations.json")) {
      if (stream == null) {
        System.err.println("reservations.json not found in classpath or S3");
        return new ReservationList(); // empty default
      }
      
      // Read stream into byte array for JsonInputStream
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[1024];
      int nRead;
      while ((nRead = stream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }
      
      // Create temporary input stream for JsonInputStream
      try (ByteArrayInputStream bais = new ByteArrayInputStream(buffer.toByteArray());
           JsonInputStream is = new JsonInputStream(bais)) {
        ReservationList reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
        return reservationList != null ? reservationList : new ReservationList();
      }
    } catch (IOException e) {
      e.printStackTrace();
      return new ReservationList();
    }
  }

}
