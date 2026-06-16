package com.acme.modres.db;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customer information service migrated for cloud-native deployment.
 * 
 * Cloud-native changes:
 * - Removed EJB 2.x annotations (@Singleton, @Startup) for Spring Boot compatibility
 * - Added proper resource management with try-with-resources
 * - Ready for Spring Boot @Service or @Component annotation
 * - Compatible with Azure App Service and container deployment
 * 
 * Migration path to Spring Boot:
 * 1. Add @Service annotation (Spring)
 * 2. Use @Autowired for DataSource injection
 * 3. Configure DataSource in application.properties with Azure SQL connection string
 * 4. Use Azure Key Vault for database credentials
 * 
 * Example Azure SQL configuration:
 * spring.datasource.url=jdbc:sqlserver://myserver.database.windows.net:1433;database=mydb
 * spring.datasource.username=${AZURE_SQL_USERNAME}
 * spring.datasource.password=${AZURE_SQL_PASSWORD}
 * 
 * For production: Store credentials in Azure Key Vault and reference via:
 * @Microsoft.KeyVault(SecretUri=https://myvault.vault.azure.net/secrets/SQL-PASSWORD/)
 */
public class ModResortsCustomerInformation {
  private static final Logger logger = Logger.getLogger(ModResortsCustomerInformation.class.getName());
  private static final String SELECT_CUSTOMERS_QUERY = "SELECT INFO FROM CUSTOMER";

  // DataSource should be injected via Spring @Autowired or CDI @Inject
  // For Azure: Configure in application.properties with Azure SQL connection string
  @Resource(lookup = "jdbc/ModResortsJndi")
  private DataSource dataSource;

  /**
   * Retrieves customer information from database.
   * Uses try-with-resources for proper resource management in cloud environments.
   * 
   * @return List of customer information strings
   */
  public ArrayList<String> getCustomerInformation() {
    ArrayList<String> customerInfo = new ArrayList<>();

    // Use try-with-resources to ensure proper resource cleanup
    // Critical for cloud environments with limited resources
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(SELECT_CUSTOMERS_QUERY);
         ResultSet rs = stmt.executeQuery()) {

      // Process the results
      while (rs.next()) {
        String info = rs.getString("INFO");
        customerInfo.add(info);
      }

    } catch (SQLException e) {
      logger.log(Level.SEVERE, "Database error while retrieving customer information", e);
      // In production, consider throwing a custom exception or returning empty list
      // For cloud monitoring, integrate with Azure Application Insights
    }
    
    return customerInfo;
  }
  
  /**
   * Sets the DataSource for this service.
   * Useful for testing and Spring Boot configuration.
   * 
   * @param dataSource The DataSource to use
   */
  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }
}
