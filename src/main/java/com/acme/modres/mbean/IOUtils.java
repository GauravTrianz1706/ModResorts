package com.acme.modres.mbean;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.acme.modres.mbean.reservation.ReservationList;
import com.acme.modres.util.JsonInputStream;

public final class IOUtils {

  private static final Logger logger = Logger.getLogger(IOUtils.class.getName());

  public static File getFileFromRelativePath(String path) {
    File file = null;
    
    // Using try-with-resources for automatic resource management
    try (InputStream initialStream = IOUtils.class.getClassLoader().getResourceAsStream(path)) {
      
      if (initialStream == null) {
        logger.log(Level.SEVERE, "Resource not found: " + path);
        return null;
      }
      
      byte[] buffer = new byte[initialStream.available()];
      initialStream.read(buffer);

      file = File.createTempFile(path, null);
      
      try (OutputStream outStream = new FileOutputStream(file)) {
        outStream.write(buffer);
      }
      
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error reading file from path: " + path, e);
    }

    return file;
  }

  public static OpMetadataList getOpListFromConfig() {
    File file = getFileFromRelativePath("ops.json");
    if (file == null) {
      return new OpMetadataList(); // empty default
    }
    
    try (JsonInputStream is = new JsonInputStream(file)) {
      OpMetadataList opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
      return opList != null ? opList : new OpMetadataList();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error loading operation list from config", e);
      return new OpMetadataList();
    }
  }

  public static ReservationList getReservationListFromConfig() {
    File file = getFileFromRelativePath("reservations.json");
    if (file == null) {
      return new ReservationList(); // empty default
    }
    
    try (JsonInputStream is = new JsonInputStream(file)) {
      ReservationList reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
      return reservationList != null ? reservationList : new ReservationList();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error loading reservation list from config", e);
      return new ReservationList();
    }
  }

}
