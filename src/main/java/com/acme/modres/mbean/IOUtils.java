package com.acme.modres.mbean;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.logging.Logger;

import com.acme.modres.mbean.reservation.ReservationList;
import com.google.gson.Gson;

/**
 * Utility class for reading configuration resources.
 * Migrated from local file system writes (FileOutputStream / createTempFile)
 * to in-memory classpath resource reading to ensure cloud compatibility.
 * In cloud/containerized environments the local file system is ephemeral;
 * writing temp files there risks data loss on container restart.
 */
public final class IOUtils {

  private static final Logger logger = Logger.getLogger(IOUtils.class.getName());

  /**
   * Reads a classpath resource and parses it as the given type using Gson.
   * Replaces the previous implementation that wrote the resource bytes to a
   * local temp file (File.createTempFile + FileOutputStream) before reading —
   * a pattern that is unsafe in cloud/containerised environments.
   *
   * @param path the classpath-relative resource path (e.g. "ops.json")
   * @param cls  the target class to deserialise into
   * @return the deserialised object, or {@code null} on error
   */
  private static <T> T parseClasspathResourceAsJson(String path, Class<T> cls) {
    InputStream inputStream = IOUtils.class.getClassLoader().getResourceAsStream(path);
    if (inputStream == null) {
      logger.warning("Classpath resource not found: " + path);
      return null;
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
      Gson gson = new Gson();
      return gson.fromJson(reader, cls);
    } catch (IOException e) {
      logger.severe("Failed to parse classpath resource '" + path + "': " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  public static OpMetadataList getOpListFromConfig() {
    return parseClasspathResourceAsJson("ops.json", OpMetadataList.class);
  }

  public static ReservationList getReservationListFromConfig() {
    return parseClasspathResourceAsJson("reservations.json", ReservationList.class);
  }

}
