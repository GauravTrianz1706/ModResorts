package com.acme.modres.mbean;

import java.io.IOException;
import java.io.InputStream;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

/**
 * Cloud-native IOUtils that reads from classpath resources instead of local file system.
 * Eliminates temporary file creation and local file system dependencies.
 */
public final class IOUtils {

  /**
   * Get resource stream from classpath - no temporary files created
   * @param path Resource path in classpath
   * @return InputStream for the resource
   */
  public static InputStream getResourceStream(String path) {
    return IOUtils.class.getClassLoader().getResourceAsStream(path);
  }

  /**
   * Load operation metadata list from classpath resource
   * @return OpMetadataList parsed from JSON
   */
  public static OpMetadataList getOpListFromConfig() {
    InputStream resourceStream = getResourceStream("ops.json");
    if (resourceStream == null) {
      System.err.println("ops.json not found in classpath");
      return new OpMetadataList(); // empty default
    }
    
    try (JsonInputStream is = new JsonInputStream(resourceStream)) {
      OpMetadataList opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      e.printStackTrace();
      return new OpMetadataList(); // empty default
    }
  }

  /**
   * Load reservation list from classpath resource
   * @return ReservationList parsed from JSON
   */
  public static ReservationList getReservationListFromConfig() {
    InputStream resourceStream = getResourceStream("reservations.json");
    if (resourceStream == null) {
      System.err.println("reservations.json not found in classpath");
      return new ReservationList(); // empty default
    }
    
    try (JsonInputStream is = new JsonInputStream(resourceStream)) {
      ReservationList reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      e.printStackTrace();
      return new ReservationList(); // empty default
    }
  }

}
