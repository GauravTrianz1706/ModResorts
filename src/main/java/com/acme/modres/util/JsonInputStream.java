package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;

/**
 * Cloud-compatible JSON input stream that works with any InputStream
 * No longer depends on File objects for cloud compatibility
 */
public class JsonInputStream extends InputStream {

  private InputStream inputStream;

  public JsonInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  @Override
  public int read() throws IOException {
    return inputStream.read();
  }

  @Override
  public int read(byte[] b) throws IOException {
    return inputStream.read(b);
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return inputStream.read(b, off, len);
  }

  @Override
  public void close() throws IOException {
    if (inputStream != null) {
      inputStream.close();
    }
  }

  public Object parseJsonAs(Class<?> cls) {
    Object jsonObject = null;
    try {
      Gson gson = new Gson();
      BufferedReader reader = new BufferedReader(new InputStreamReader(this));
      jsonObject = gson.fromJson(reader, cls);
    } catch (Exception e) {
      System.err.println("Failed to parse JSON: " + e.getMessage());
      e.printStackTrace();
    }
    return jsonObject;
  }

}
