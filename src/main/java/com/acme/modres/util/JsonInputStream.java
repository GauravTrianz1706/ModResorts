package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;

/**
 * JSON parsing utility that reads from an in-memory byte array.
 *
 * Refactored from FileInputStream-based implementation to support cloud-native
 * in-memory processing without local file system dependencies. The previous
 * File-based constructor has been replaced with a byte[]-based constructor to
 * eliminate java.io.File usage for data storage (cr-java-0063) and local
 * temporary file writes (cr-java-0062, cr-java-0112).
 */
public class JsonInputStream implements Closeable {

  private final InputStream inputStream;

  /**
   * Creates a JsonInputStream backed by an in-memory byte array.
   * No local file system access required.
   *
   * @param data the raw JSON bytes to parse
   */
  public JsonInputStream(byte[] data) {
    this.inputStream = new ByteArrayInputStream(data);
  }

  /**
   * Parses the JSON content as the given class type.
   *
   * @param cls the target class
   * @return the parsed object, or null on failure
   */
  public Object parseJsonAs(Class<?> cls) {
    try {
      Gson gson = new Gson();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
      return gson.fromJson(reader, cls);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @Override
  public void close() throws IOException {
    if (inputStream != null) {
      inputStream.close();
    }
  }
}
