# ModResorts - Azure Cloud Deployment Guide

## Overview
ModResorts application has been migrated for cloud-native deployment on Microsoft Azure. The application is now packaged as a Spring Boot executable JAR with embedded Tomcat server, eliminating the need for external application servers.

## Cloud Readiness Fixes Applied

### 1. File System Dependencies Eliminated
- **Issue**: Hard-coded file paths and local file system operations
- **Fix**: Migrated to classpath resources and in-memory processing
- **Azure Integration**: Ready for Azure Blob Storage integration
- **Files Modified**: 
  - `AvailabilityCheckerServlet.java`
  - `IOUtils.java`
  - `JsonInputStream.java`

### 2. Secrets Management Externalized
- **Issue**: Hard-coded API keys in source code
- **Fix**: Externalized to environment variables
- **Azure Integration**: Azure Key Vault with Managed Identity
- **Files Modified**: `WeatherServlet.java`

### 3. EJB 2.x Removed
- **Issue**: Heavy EJB container dependencies
- **Fix**: Migrated to Spring Boot with proper resource management
- **Azure Integration**: Compatible with Azure App Service
- **Files Modified**: `ModResortsCustomerInformation.java`

### 4. Timer Dependencies Replaced
- **Issue**: Local java.util.Timer not suitable for distributed cloud
- **Fix**: Timezone-aware date handling, ready for Azure Service Bus
- **Azure Integration**: Azure Service Bus Scheduled Messages or Azure Functions
- **Files Modified**: 
  - `DateChecker.java`
  - `ReservationCheckerData.java`

### 5. WAR to Executable JAR
- **Issue**: WAR packaging requires external application server
- **Fix**: Spring Boot executable JAR with embedded Tomcat
- **Azure Integration**: Direct deployment to Azure App Service
- **Files Modified**: `pom.xml`

### 6. Resource Leak Prevention
- **Issue**: Unclosed resources causing memory leaks
- **Fix**: Try-with-resources for automatic resource management
- **Azure Integration**: Critical for container resource limits
- **Files Modified**: Multiple files with proper resource cleanup

## Azure Services Integration

### Required Azure Services
1. **Azure App Service** - Application hosting
2. **Azure SQL Database** - Data persistence
3. **Azure Key Vault** - Secrets management
4. **Azure Blob Storage** - File storage
5. **Azure Application Insights** - Monitoring and telemetry

### Optional Azure Services
- **Azure Service Bus** - Distributed scheduling
- **Azure Functions** - Timer-triggered operations
- **Azure Container Apps** - Serverless container deployment
- **Azure Kubernetes Service (AKS)** - Container orchestration

## Deployment Instructions

### Prerequisites
- Azure CLI installed and configured
- Maven 3.6+ installed
- Java 8 or higher
- Azure subscription

### Step 1: Build the Application
```bash
mvn clean package
```

This creates an executable JAR: `target/modresorts-2.0.0.jar`

### Step 2: Create Azure Resources

#### Create Resource Group
```bash
az group create --name modresorts-rg --location eastus
```

#### Create Azure SQL Database
```bash
az sql server create \
  --name modresorts-sql-server \
  --resource-group modresorts-rg \
  --location eastus \
  --admin-user sqladmin \
  --admin-password <YourPassword>

az sql db create \
  --resource-group modresorts-rg \
  --server modresorts-sql-server \
  --name modresorts-db \
  --service-objective S0
```

#### Create Azure Key Vault
```bash
az keyvault create \
  --name modresorts-keyvault \
  --resource-group modresorts-rg \
  --location eastus
```

#### Store Secrets in Key Vault
```bash
az keyvault secret set \
  --vault-name modresorts-keyvault \
  --name AZURE-SQL-PASSWORD \
  --value <YourSQLPassword>

az keyvault secret set \
  --vault-name modresorts-keyvault \
  --name WEATHER-API-KEY \
  --value <YourWeatherAPIKey>
```

#### Create Azure Blob Storage
```bash
az storage account create \
  --name modresortsstorage \
  --resource-group modresorts-rg \
  --location eastus \
  --sku Standard_LRS

az storage container create \
  --name modresorts-data \
  --account-name modresortsstorage
```

#### Create Azure App Service
```bash
az appservice plan create \
  --name modresorts-plan \
  --resource-group modresorts-rg \
  --sku P1V2 \
  --is-linux

az webapp create \
  --name modresorts-app \
  --resource-group modresorts-rg \
  --plan modresorts-plan \
  --runtime "JAVA|8-jre8"
```

### Step 3: Enable Managed Identity
```bash
az webapp identity assign \
  --name modresorts-app \
  --resource-group modresorts-rg
```

### Step 4: Grant Managed Identity Access

#### Key Vault Access
```bash
az keyvault set-policy \
  --name modresorts-keyvault \
  --object-id <managed-identity-principal-id> \
  --secret-permissions get list
```

#### Blob Storage Access
```bash
az role assignment create \
  --assignee <managed-identity-principal-id> \
  --role "Storage Blob Data Contributor" \
  --scope /subscriptions/<subscription-id>/resourceGroups/modresorts-rg/providers/Microsoft.Storage/storageAccounts/modresortsstorage
```

### Step 5: Configure Application Settings
```bash
az webapp config appsettings set \
  --name modresorts-app \
  --resource-group modresorts-rg \
  --settings \
    AZURE_SQL_CONNECTION_STRING="jdbc:sqlserver://modresorts-sql-server.database.windows.net:1433;database=modresorts-db" \
    AZURE_SQL_USERNAME="sqladmin" \
    AZURE_SQL_PASSWORD="@Microsoft.KeyVault(SecretUri=https://modresorts-keyvault.vault.azure.net/secrets/AZURE-SQL-PASSWORD/)" \
    WEATHER_API_KEY="@Microsoft.KeyVault(SecretUri=https://modresorts-keyvault.vault.azure.net/secrets/WEATHER-API-KEY/)" \
    AZURE_KEYVAULT_URI="https://modresorts-keyvault.vault.azure.net/" \
    AZURE_STORAGE_BLOB_ENDPOINT="https://modresortsstorage.blob.core.windows.net" \
    AZURE_STORAGE_CONTAINER_NAME="modresorts-data"
```

### Step 6: Deploy Application

#### Option A: Using Maven Plugin
```bash
mvn azure-webapp:deploy
```

#### Option B: Using Azure CLI
```bash
az webapp deploy \
  --name modresorts-app \
  --resource-group modresorts-rg \
  --src-path target/modresorts-2.0.0.jar \
  --type jar
```

### Step 7: Verify Deployment
```bash
# Check application logs
az webapp log tail \
  --name modresorts-app \
  --resource-group modresorts-rg

# Test health endpoint
curl https://modresorts-app.azurewebsites.net/actuator/health
```

## Configuration Reference

### Environment Variables
All configuration is externalized via environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `AZURE_SQL_CONNECTION_STRING` | Azure SQL connection string | `jdbc:sqlserver://...` |
| `AZURE_SQL_USERNAME` | Database username | `sqladmin` |
| `AZURE_SQL_PASSWORD` | Database password (from Key Vault) | `@Microsoft.KeyVault(...)` |
| `WEATHER_API_KEY` | Weather API key (from Key Vault) | `@Microsoft.KeyVault(...)` |
| `AZURE_KEYVAULT_URI` | Key Vault URI | `https://....vault.azure.net/` |
| `AZURE_STORAGE_BLOB_ENDPOINT` | Blob Storage endpoint | `https://....blob.core.windows.net` |
| `AZURE_STORAGE_CONTAINER_NAME` | Storage container name | `modresorts-data` |

### Application Endpoints
- Health Check: `/actuator/health`
- Weather API: `/resorts/weather?selectedCity=<city>`
- Availability Check: `/resorts/availability?date=<date>`

## Monitoring and Troubleshooting

### Application Insights
Application Insights is automatically configured for monitoring:
- Request telemetry
- Dependency tracking
- Exception tracking
- Custom metrics

### Logs
View application logs:
```bash
az webapp log tail --name modresorts-app --resource-group modresorts-rg
```

### Common Issues

#### Database Connection Failures
- Verify SQL Server firewall rules allow Azure services
- Check Managed Identity has database access
- Validate connection string in Application Settings

#### Key Vault Access Denied
- Ensure Managed Identity is enabled
- Verify Key Vault access policies
- Check Key Vault reference format in Application Settings

#### Blob Storage Access Denied
- Verify Managed Identity has "Storage Blob Data Contributor" role
- Check storage account firewall settings
- Validate blob endpoint configuration

## Security Best Practices

1. **Never commit secrets to source code**
2. **Use Azure Key Vault for all sensitive data**
3. **Enable Managed Identity for authentication**
4. **Use HTTPS for all endpoints**
5. **Enable Azure DDoS Protection**
6. **Configure network security groups**
7. **Enable Azure Security Center**
8. **Regular security updates and patches**

## Performance Optimization

1. **HikariCP Connection Pool**: Configured for cloud environments
2. **Application Insights**: Monitor performance metrics
3. **Azure CDN**: For static content delivery
4. **Auto-scaling**: Configure based on load
5. **Azure Front Door**: For global load balancing

## Cost Optimization

1. **Right-size App Service Plan**: Start with P1V2, scale as needed
2. **Azure SQL DTU**: Monitor and adjust based on usage
3. **Blob Storage Tiers**: Use appropriate access tiers
4. **Reserved Instances**: For predictable workloads
5. **Azure Cost Management**: Monitor and set budgets

## Support and Documentation

- Azure Documentation: https://docs.microsoft.com/azure
- Spring Boot on Azure: https://docs.microsoft.com/azure/developer/java/spring-framework/
- Azure App Service: https://docs.microsoft.com/azure/app-service/

## License
[Your License Here]
