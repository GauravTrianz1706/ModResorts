package com.acme.modres.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
      Object jsonObject = null;
      // Using try-with-resources for automatic resource management
      try (JsonInputStream is = new JsonInputStream(file);
           InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
           BufferedReader reader = new BufferedReader(isr)) {
        
        Gson gson = new Gson();
        jsonObject = gson.fromJson(reader, cls);
      } catch (IOException e) {
        logger.log(Level.SEVERE, "IO error while parsing JSON", e);
      } catch (Exception e) {
        logger.log(Level.SEVERE, "Error parsing JSON", e);
      }
      return jsonObject;
    }
    return null;
  }

}
