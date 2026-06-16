package com.acme.modres.mbean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

/**
 * Cloud-native utility class for reading configuration resources.
 * Migrated from local file system operations to classpath resources and in-memory processing.
 * 
 * For Azure cloud deployment:
 * - Configuration files are packaged in the application JAR/WAR
 * - No temporary file creation on local file system
 * - All processing done in memory
 * - For larger files, integrate with Azure Blob Storage
 */
public final class IOUtils {

  /**
   * Reads a resource from classpath and returns it as an InputStream.
   * This replaces the previous approach of creating temporary files on local file system.
   * 
   * @param path Resource path relative to classpath
   * @return InputStream of the resource, or null if not found
   */
  public static InputStream getResourceAsStream(String path) {
    try {
      InputStream initialStream = IOUtils.class.getClassLoader().getResourceAsStream(path);
      if (initialStream == null) {
        System.err.println("Resource not found: " + path);
        return null;
      }
      
      // Read into memory to avoid keeping the original stream open
      ByteArrayOutputStream buffer = new ByteArrayOutputStream();
      byte[] data = new byte[1024];
      int bytesRead;
      
      while ((bytesRead = initialStream.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, bytesRead);
      }
      
      initialStream.close();
      
      // Return a new ByteArrayInputStream that can be used independently
      return new ByteArrayInputStream(buffer.toByteArray());
      
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Legacy method maintained for backward compatibility.
   * Now returns an InputStream instead of File to avoid file system dependencies.
   * 
   * @deprecated Use getResourceAsStream() directly for cloud-native approach
   */
  @Deprecated
  public static InputStream getFileFromRelativePath(String path) {
    return getResourceAsStream(path);
  }

  public static OpMetadataList getOpListFromConfig() {
    try (InputStream resourceStream = getResourceAsStream("ops.json")) {
      if (resourceStream == null) {
        System.err.println("ops.json not found in classpath");
        return new OpMetadataList(); // Return empty default
      }
      
      try (JsonInputStream is = new JsonInputStream(resourceStream)) {
        OpMetadataList opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
        return opList != null ? opList : new OpMetadataList();
      }
    } catch (IOException e) {
      e.printStackTrace();
      return new OpMetadataList(); // Return empty default on error
    }
  }

  public static ReservationList getReservationListFromConfig() {
    try (InputStream resourceStream = getResourceAsStream("reservations.json")) {
      if (resourceStream == null) {
        System.err.println("reservations.json not found in classpath");
        return new ReservationList(); // Return empty default
      }
      
      try (JsonInputStream is = new JsonInputStream(resourceStream)) {
        ReservationList reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
        return reservationList != null ? reservationList : new ReservationList();
      }
    } catch (IOException e) {
      e.printStackTrace();
      return new ReservationList(); // Return empty default on error
    }
  }

}
