package com.acme.modres.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.logging.Logger;

/**
 * Azure Cloud Configuration for ModResorts application.
 * 
 * This configuration class provides Azure-specific beans for:
 * - Azure Blob Storage client
 * - Azure Key Vault integration (via Spring Boot starter)
 * - Managed Identity authentication
 * 
 * Deployment notes:
 * 1. Enable Managed Identity on Azure App Service or AKS
 * 2. Grant Managed Identity access to:
 *    - Azure Key Vault (for secrets)
 *    - Azure Blob Storage (for file operations)
 *    - Azure SQL Database (for data access)
 * 3. Configure environment variables in Azure App Service Application Settings
 */
@Configuration
public class AzureConfiguration {
    
    private static final Logger logger = Logger.getLogger(AzureConfiguration.class.getName());

    @Value("${azure.storage.blob-endpoint:}")
    private String blobEndpoint;

    @Value("${azure.storage.account-name:}")
    private String storageAccountName;

    /**
     * Creates Azure Blob Storage client using Managed Identity.
     * 
     * This replaces local file system operations with cloud storage.
     * Managed Identity provides secure, credential-free authentication.
     * 
     * @return BlobServiceClient for Azure Blob Storage operations
     */
    @Bean
    @Profile("!test")
    public BlobServiceClient blobServiceClient() {
        if (blobEndpoint == null || blobEndpoint.isEmpty()) {
            logger.warning("Azure Blob Storage endpoint not configured. File operations will be limited.");
            logger.info("Configure AZURE_STORAGE_BLOB_ENDPOINT in Application Settings");
            return null;
        }

        try {
            // Use DefaultAzureCredential for Managed Identity authentication
            // This works automatically in Azure App Service, AKS, and local development (Azure CLI)
            BlobServiceClient client = new BlobServiceClientBuilder()
                .endpoint(blobEndpoint)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
            
            logger.info("Azure Blob Storage client initialized successfully");
            return client;
            
        } catch (Exception e) {
            logger.severe("Failed to initialize Azure Blob Storage client: " + e.getMessage());
            logger.info("Ensure Managed Identity is enabled and has 'Storage Blob Data Contributor' role");
            return null;
        }
    }

    /**
     * Configuration for Azure Key Vault integration.
     * 
     * Azure Key Vault is configured via Spring Boot starter properties:
     * - azure.keyvault.uri
     * - azure.keyvault.tenant-id
     * 
     * Secrets are automatically injected as environment variables.
     * Example: @Value("${WEATHER-API-KEY}") retrieves secret from Key Vault
     */
    // Key Vault configuration is handled by azure-spring-boot-starter-keyvault-secrets
    // No additional bean configuration needed
}
