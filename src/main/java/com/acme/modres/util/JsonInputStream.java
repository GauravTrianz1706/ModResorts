package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;

/**
 * JsonInputStream - supports parsing JSON from either a File or a byte array.
 * The byte-array constructor enables cloud-native usage where content is
 * retrieved from Amazon S3 or classpath resources without writing to the
 * local file system.
 */
public class JsonInputStream implements AutoCloseable {

  private final InputStream inputStream;
  private final File file; // kept for backward compatibility; null when using byte[]

  /**
   * Construct from a File (legacy path, kept for backward compatibility).
   */
  public JsonInputStream(File file) throws FileNotFoundException {
    this.file = file;
    this.inputStream = new FileInputStream(file);
  }

  /**
   * Construct from a byte array — no local file system access required.
   * Used when content is sourced from classpath resources or Amazon S3.
   */
  public JsonInputStream(byte[] data) {
    this.file = null;
    this.inputStream = new ByteArrayInputStream(data);
  }

  /**
   * Parse the JSON content of this stream as the given class type.
   *
   * @param cls target class
   * @return parsed object, or null on error
   */
  public Object parseJsonAs(Class<?> cls) {
    try {
      Gson gson = new Gson();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
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
