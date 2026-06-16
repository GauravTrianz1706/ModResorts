package com.acme.modres;

import com.acme.modres.db.ModResortsCustomerInformation;
import com.acme.modres.exception.ExceptionHandler;
import com.acme.modres.mbean.AppInfo;

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.inject.Inject;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.annotation.WebServlet;

/**
 * Weather servlet migrated for Azure cloud deployment.
 * 
 * Cloud-native changes:
 * - API keys externalized to environment variables (should be stored in Azure Key Vault)
 * - Removed WebSphere-specific dependencies
 * - Ready for Azure App Service deployment
 * 
 * Azure Key Vault Integration (recommended):
 * 1. Store WEATHER_API_KEY in Azure Key Vault
 * 2. Use Managed Identity to access Key Vault
 * 3. Reference secrets via environment variables or Azure App Configuration
 * 
 * Example Azure Key Vault setup:
 * - Create Key Vault: az keyvault create --name myKeyVault --resource-group myRG
 * - Store secret: az keyvault secret set --vault-name myKeyVault --name WEATHER-API-KEY --value "your-api-key"
 * - Enable Managed Identity on App Service
 * - Grant App Service access to Key Vault
 * - Reference in App Service: @Microsoft.KeyVault(SecretUri=https://myKeyVault.vault.azure.net/secrets/WEATHER-API-KEY/)
 */
@WebServlet({ "/resorts/weather" })
public class WeatherServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Inject
  private ModResortsCustomerInformation customerInfo;

  // Environment variable key name for Weather API key
  // In Azure, this should be configured in App Service Application Settings
  // and sourced from Azure Key Vault using Key Vault references
  private static final String WEATHER_API_KEY = "WEATHER_API_KEY";

  private static final Logger logger = Logger.getLogger(WeatherServlet.class.getName());

  private static InitialContext context;

  MBeanServer server;
  ObjectName weatherON;
  ObjectInstance mbean;

  @Override
  public void init() {
    server = ManagementFactory.getPlatformMBeanServer();
    try {
      weatherON = new ObjectName("com.acme.modres.mbean:name=appInfo");
    } catch (MalformedObjectNameException e) {
      logger.log(Level.SEVERE, "Failed to create MBean ObjectName", e);
    }
    try {
      if (weatherON != null) {
        mbean = server.registerMBean(new AppInfo(), weatherON);
      }
    } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
      logger.log(Level.WARNING, "Failed to register MBean", e);
    }
    
    // Initialize context without WebSphere-specific properties for cloud compatibility
    context = setInitialContextProps();
  }

  @Override
  public void destroy() {
    if (mbean != null) {
      try {
        server.unregisterMBean(weatherON);
      } catch (MBeanRegistrationException | InstanceNotFoundException e) {
        logger.log(Level.WARNING, "Failed to unregister MBean", e);
      }
    }
  }

  @Override
  protected void doGet(HttpServletRequest request,
      HttpServletResponse response) throws IOException, ServletException {

    String methodName = "doGet";
    logger.entering(WeatherServlet.class.getName(), methodName);

    try {
      MBeanInfo weatherConfig = server.getMBeanInfo(weatherON);
    } catch (IntrospectionException | InstanceNotFoundException | ReflectionException e) {
      logger.log(Level.WARNING, "Failed to get MBean info", e);
    }

    String city = request.getParameter("selectedCity");
    logger.log(Level.FINE, "requested city is " + city);

    // Retrieve API key from environment variable
    // In Azure App Service, configure this in Application Settings
    // Best practice: Use Azure Key Vault reference format:
    // @Microsoft.KeyVault(SecretUri=https://myvault.vault.azure.net/secrets/WEATHER-API-KEY/)
    String weatherAPIKey = System.getenv(WEATHER_API_KEY);
    String mockedKey = mockKey(weatherAPIKey);
    logger.log(Level.FINE, "weatherAPIKey is " + mockedKey);

    if (weatherAPIKey != null && weatherAPIKey.trim().length() > 0) {
      logger.info("weatherAPIKey is found, system will provide the real time weather data for the city " + city);
      getRealTimeWeatherData(city, weatherAPIKey, response);
    } else {
      logger.info(
          "weatherAPIKey is not found in environment variables. Using default weather data for the city " + city);
      logger.info("For production: Configure WEATHER_API_KEY in Azure App Service Application Settings");
      logger.info("Recommended: Store in Azure Key Vault and reference via @Microsoft.KeyVault(SecretUri=...)");
      getDefaultWeatherData(city, response);
    }
  }

  private void getRealTimeWeatherData(String city, String apiKey, HttpServletResponse response)
      throws ServletException, IOException {
    String resturl = null;
    String resturlbase = Constants.WUNDERGROUND_API_PREFIX + apiKey + Constants.WUNDERGROUND_API_PART;

    if (Constants.PARIS.equals(city)) {
      resturl = resturlbase + "France/Paris.json";
    } else if (Constants.LAS_VEGAS.equals(city)) {
      resturl = resturlbase + "NV/Las_Vegas.json";
    } else if (Constants.SAN_FRANCISCO.equals(city)) {
      resturl = resturlbase + "/CA/San_Francisco.json";
    } else if (Constants.MIAMI.equals(city)) {
      resturl = resturlbase + "FL/Miami.json";
    } else if (Constants.CORK.equals(city)) {
      resturl = resturlbase + "ireland/cork.json";
    } else if (Constants.BARCELONA.equals(city)) {
      resturl = resturlbase + "Spain/Barcelona.json";
    } else {
      String errorMsg = "Sorry, the weather information for your selected city: " + city +
          " is not available.  Valid selections are: " + Constants.SUPPORTED_CITIES;
      ExceptionHandler.handleException(null, errorMsg, logger);
    }

    URL obj = null;
    HttpURLConnection con = null;
    try {
      obj = new URL(resturl);
      con = (HttpURLConnection) obj.openConnection();
      con.setRequestMethod("GET");
    } catch (MalformedURLException e1) {
      String errorMsg = "Caught MalformedURLException. Please make sure the url is correct.";
      ExceptionHandler.handleException(e1, errorMsg, logger);
    } catch (ProtocolException e2) {
      String errorMsg = "Caught ProtocolException: " + e2.getMessage()
          + ". Not able to set request method to http connection.";
      ExceptionHandler.handleException(e2, errorMsg, logger);
    } catch (IOException e3) {
      String errorMsg = "Caught IOException: " + e3.getMessage() + ". Not able to open connection.";
      ExceptionHandler.handleException(e3, errorMsg, logger);
    }

    int responseCode = con.getResponseCode();
    logger.log(Level.FINEST, "Response Code: " + responseCode);

    if (responseCode >= 200 && responseCode < 300) {

      BufferedReader in = null;
      ServletOutputStream out = null;

      try {
        in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine = null;
        StringBuffer responseStr = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
          responseStr.append(inputLine);
        }

        response.setContentType("application/json");
        out = response.getOutputStream();
        out.print(responseStr.toString());
        logger.log(Level.FINE, "responseStr: " + responseStr);
      } catch (Exception e) {
        String errorMsg = "Problem occured when processing the weather server response.";
        ExceptionHandler.handleException(e, errorMsg, logger);
      } finally {
        if (in != null) {
          in.close();
        }
        if (out != null) {
          out.close();
        }
        in = null;
        out = null;
      }
    } else {
      String errorMsg = "REST API call " + resturl + " returns an error response: " + responseCode;
      ExceptionHandler.handleException(null, errorMsg, logger);
    }
  }

  private void getDefaultWeatherData(String city, HttpServletResponse response)
      throws ServletException, IOException {
    DefaultWeatherData defaultWeatherData = null;

    try {
      defaultWeatherData = new DefaultWeatherData(city);
    } catch (UnsupportedOperationException e) {
      ExceptionHandler.handleException(e, e.getMessage(), logger);
    }

    ServletOutputStream out = null;

    try {
      String responseStr = defaultWeatherData.getDefaultWeatherData();
      response.setContentType("application/json");
      out = response.getOutputStream();
      out.print(responseStr.toString());
      logger.log(Level.FINEST, "responseStr: " + responseStr);
    } catch (Exception e) {
      String errorMsg = "Problem occured when getting the default weather data.";
      ExceptionHandler.handleException(e, errorMsg, logger);
    } finally {

      if (out != null) {
        out.close();
      }

      out = null;
    }
  }

  /**
   * Returns the weather information for a given city
   */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    doGet(request, response);
  }

  private static String mockKey(String toBeMocked) {
    if (toBeMocked == null) {
      return null;
    }
    String lastToKeep = toBeMocked.substring(toBeMocked.length() - 3);
    return "*********" + lastToKeep;
  }

  /**
   * Removed WebSphere-specific server name discovery for cloud compatibility.
   * In Azure, use environment variables or Azure App Configuration for environment discovery.
   * 
   * @deprecated WebSphere-specific, not compatible with cloud environments
   */
  @Deprecated
  private String configureEnvDiscovery() {
    // Cloud-native alternative: Use environment variables
    String serverEnv = System.getenv("ENVIRONMENT"); // e.g., "dev", "staging", "production"
    if (serverEnv == null) {
      serverEnv = System.getProperty("app.environment", "unknown");
    }
    return serverEnv;
  }

  /**
   * Initialize JNDI context with cloud-compatible settings.
   * Removed WebSphere-specific JNDI configuration.
   */
  private InitialContext setInitialContextProps() {
    InitialContext ctx = null;
    try {
      // Use default JNDI context for cloud environments
      // Cloud platforms (Azure App Service, AKS) provide their own JNDI implementations
      ctx = new InitialContext();
    } catch (NamingException e) {
      logger.log(Level.WARNING, "Failed to create InitialContext. JNDI may not be available in this environment.", e);
    }

    return ctx;
  }
}
