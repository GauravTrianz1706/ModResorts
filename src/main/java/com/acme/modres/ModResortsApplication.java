package com.acme.modres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * Spring Boot Application class for ModResorts.
 * 
 * Cloud-native deployment for Azure:
 * - Embedded Tomcat server (no external application server needed)
 * - Self-contained executable JAR
 * - Ready for Azure App Service, AKS, or Container Apps
 * 
 * Configuration:
 * - application.properties for environment-specific settings
 * - Azure Key Vault for secrets management
 * - Azure Application Insights for monitoring
 * 
 * Deployment options:
 * 1. Azure App Service: Deploy JAR directly
 * 2. Azure Kubernetes Service (AKS): Containerize and deploy
 * 3. Azure Container Apps: Serverless container deployment
 */
@SpringBootApplication
@ServletComponentScan // Enable scanning for @WebServlet annotations
public class ModResortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModResortsApplication.class, args);
    }
}
