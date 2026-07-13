package com.acme.modres.security;

import java.util.logging.Logger;
import java.util.logging.Level;

public class Service {
  private static final Logger logger = Logger.getLogger(Service.class.getName());
  public static final String OPERATION = "my-operation";

  public void operation() {
    // SecurityManager is deprecated and removed in Java 17+
    // Removed SecurityManager usage as it's no longer available
    // Security checks should be implemented using modern security frameworks
    logger.log(Level.INFO, "Operation is executed");
    System.out.println("Operation is executed");
  }
}
