package com.acme.modres.mbean;

import java.io.IOException;
import java.io.InputStream;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

public final class IOUtils {

  private static S3Client s3Client;
  private static String s3BucketName;
  
  static {
    // Initialize S3 client for cloud storage
    s3Client = S3Client.builder()
        .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
        .build();
    s3BucketName = System.getenv().getOrDefault("S3_BUCKET_NAME", "modresorts-data");
  }

  /**
   * Get resource from classpath (preferred) or S3 as fallback
   */
  public static InputStream getResourceStream(String path) throws IOException {
    // First try to load from classpath
    InputStream classpathStream = IOUtils.class.getClassLoader().getResourceAsStream(path);
    if (classpathStream != null) {
      return classpathStream;
    }
    
    // Fallback to S3 if not in classpath
    try {
      GetObjectRequest getObjectRequest = GetObjectRequest.builder()
          .bucket(s3BucketName)
          .key("config/" + path)
          .build();
      
      ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest);
      return s3Stream;
    } catch (Exception e) {
      throw new IOException("Resource not found in classpath or S3: " + path, e);
    }
  }

  public static OpMetadataList getOpListFromConfig() {
    try (InputStream is = getResourceStream("ops.json");
         JsonInputStream jis = new JsonInputStream(is)) {
      OpMetadataList opList = new OpMetadataList(); // empty default
      opList = (OpMetadataList) jis.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public static ReservationList getReservationListFromConfig() {
    try (InputStream is = getResourceStream("reservations.json");
         JsonInputStream jis = new JsonInputStream(is)) {
      ReservationList reservationList = new ReservationList(); // empty default
      reservationList = (ReservationList) jis.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

}
