package com.acme.modres.mbean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public final class IOUtils {

  private static final String GCS_BUCKET_NAME = System.getenv().getOrDefault("GCS_BUCKET_NAME", "modresorts-config");
  private static Storage storage;

  static {
    try {
      storage = StorageOptions.getDefaultInstance().getService();
    } catch (Exception e) {
      System.err.println("Failed to initialize Google Cloud Storage client: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Retrieves file content from Google Cloud Storage or falls back to classpath resources.
   * This eliminates local file system dependencies for cloud-native deployment.
   */
  public static InputStream getResourceAsStream(String path) {
    InputStream stream = null;
    
    // Try to load from GCS first
    if (storage != null) {
      try {
        Blob blob = storage.get(GCS_BUCKET_NAME, path);
        if (blob != null && blob.exists()) {
          byte[] content = blob.getContent();
          stream = new ByteArrayInputStream(content);
          return stream;
        }
      } catch (Exception e) {
        System.err.println("Failed to load from GCS, falling back to classpath: " + e.getMessage());
      }
    }
    
    // Fallback to classpath resources
    stream = IOUtils.class.getClassLoader().getResourceAsStream(path);
    return stream;
  }

  /**
   * Writes content to Google Cloud Storage instead of local file system.
   * This ensures data persistence across container restarts and scaling events.
   */
  public static void writeToGCS(String path, byte[] content) throws IOException {
    if (storage == null) {
      throw new IOException("Google Cloud Storage client not initialized");
    }
    
    try {
      storage.create(
        com.google.cloud.storage.BlobInfo.newBuilder(GCS_BUCKET_NAME, path).build(),
        content
      );
    } catch (Exception e) {
      throw new IOException("Failed to write to GCS: " + e.getMessage(), e);
    }
  }

  public static OpMetadataList getOpListFromConfig() {
    try (InputStream is = getResourceAsStream("ops.json")) {
      if (is == null) {
        System.err.println("ops.json not found in GCS or classpath");
        return new OpMetadataList(); // empty default
      }
      
      // Convert InputStream to JsonInputStream compatible format
      JsonInputStream jsonIs = new JsonInputStream(is);
      OpMetadataList opList = (OpMetadataList) jsonIs.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      e.printStackTrace();
      return new OpMetadataList(); // empty default
    }
  }

  public static ReservationList getReservationListFromConfig() {
    try (InputStream is = getResourceAsStream("reservations.json")) {
      if (is == null) {
        System.err.println("reservations.json not found in GCS or classpath");
        return new ReservationList(); // empty default
      }
      
      // Convert InputStream to JsonInputStream compatible format
      JsonInputStream jsonIs = new JsonInputStream(is);
      ReservationList reservationList = (ReservationList) jsonIs.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      e.printStackTrace();
      return new ReservationList(); // empty default
    }
  }
}
