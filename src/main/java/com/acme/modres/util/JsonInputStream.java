package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.google.gson.Gson;

/**
 * Cloud-native JSON input stream that works with any InputStream,
 * eliminating dependency on local file system.
 */
public class JsonInputStream implements AutoCloseable {

  private InputStream inputStream;

  public JsonInputStream(InputStream inputStream) {
    this.inputStream = inputStream;
  }

  public Object parseJsonAs(Class<?> cls) {
    if (inputStream == null) {
      return null;
    }
    
    Object jsonObject = null;
    try {
      Gson gson = new Gson();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      jsonObject = gson.fromJson(reader, cls);
    } catch (Exception e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    }
    return jsonObject;
  }

  @Override
  public void close() throws IOException {
    if (inputStream != null) {
      try {
        inputStream.close();
      } catch (IOException e) {
        // Log but don't throw
        System.err.println("Error closing input stream: " + e.getMessage());
      }
    }
  }
}
