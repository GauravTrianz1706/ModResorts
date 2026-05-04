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
    InputStream initialStream = null;
    OutputStream outStream = null;
    try {
      initialStream = IOUtils.class.getClassLoader().getResourceAsStream(path);
      byte[] buffer = new byte[initialStream.available()];
      initialStream.read(buffer);

      file = File.createTempFile(path, null);
      outStream = new FileOutputStream(file);
      outStream.write(buffer);
      outStream.close();
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error reading file from relative path: " + path, e);
    } finally {
      if (initialStream != null) {
        try {
          initialStream.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Error closing input stream", e);
        }
      } else if (outStream != null) {
        try {
          outStream.close();
        } catch (IOException e) {
          logger.log(Level.WARNING, "Error closing output stream", e);
        }
      }
    }

    return file;
  }

  public static OpMetadataList getOpListFromConfig() {
    File file = getFileFromRelativePath("ops.json"); // fix hardcoded paths
    try (JsonInputStream is = new JsonInputStream(file)) {
      OpMetadataList opList = new OpMetadataList(); // empty default
      opList = (OpMetadataList) is.parseJsonAs(OpMetadataList.class);
      return opList;
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error parsing ops.json", e);
      return null;
    }
  }

  public static ReservationList getReservationListFromConfig() {
    File file = getFileFromRelativePath("reservations.json"); // fix hardcoded paths
    try (JsonInputStream is = new JsonInputStream(file)) {
      ReservationList reservationList = new ReservationList(); // empty default
      reservationList = (ReservationList) is.parseJsonAs(ReservationList.class);
      return reservationList;
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Error parsing reservations.json", e);
      return null;
    }
  }

}
