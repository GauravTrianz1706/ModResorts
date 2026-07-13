package com.acme.modres.util;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.logging.Logger;
import java.util.logging.Level;

public class ZipValidator extends ZipFile {

  private static final Logger logger = Logger.getLogger(ZipValidator.class.getName());
  private File file;

  public ZipValidator(File file) throws ZipException, IOException {
    super(file);
    this.file = file;
  }

  public boolean isValid() {
    if (file.exists()) {
      // Using try-with-resources for automatic resource management
      try (ZipValidator zipFile = new ZipValidator(file)) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        if (!entries.hasMoreElements()) {
          return true;
        }
      } catch (IOException e) {
        logger.log(Level.SEVERE, "Error validating zip file", e);
        return false;
      }
    }
    return false;
  }

}
