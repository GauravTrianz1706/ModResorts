package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.google.gson.Gson;

public class JsonInputStream extends FileInputStream {
  private static final Logger logger = Logger.getLogger(JsonInputStream.class.getName());

  private File file;

  public JsonInputStream(File file) throws FileNotFoundException {
    super(file);
    this.file = file;
  }

  public Object parseJsonAs(Class<?> cls) {
    if (file.exists()) {
      JsonInputStream is = null;
      Object jsonObject = null;
      try {
        is = new JsonInputStream(file);
        Gson gson = new Gson();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        jsonObject = gson.fromJson(reader, cls);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Error parsing JSON file: " + file.getName(), e);
      } catch (Throwable e) {
        logger.log(Level.SEVERE, "Unexpected error parsing JSON file: " + file.getName(), e);
      } finally {
        if (is != null) {
          try {
            is.close();
            is.read(); // test if file is closed
          } catch (IOException e) {
            // closed successfully
            return jsonObject;
          } catch (Throwable e) {
            logger.log(Level.WARNING, "Error verifying file closure", e);
          }
        }
      }
    }
    return null;
  }

}
