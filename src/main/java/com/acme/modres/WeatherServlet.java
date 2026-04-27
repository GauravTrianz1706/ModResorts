package com.acme.modres;

import com.acme.modres.db.ModResortsCustomerInformation;
import com.acme.modres.exception.ExceptionHandler;
import com.acme.modres.mbean.AppInfo;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
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
import javax.servlet.annotation.WebServlet;

@WebServlet({ "/resorts/weather" })
public class WeatherServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  @Inject
  private ModResortsCustomerInformation customerInfo;

  // Google Secret Manager configuration
  private static final String GCP_PROJECT_ID = System.getenv().getOrDefault("GCP_PROJECT_ID", "");
  private static final String WEATHER_API_KEY_SECRET_NAME = System.getenv().getOrDefault("WEATHER_API_KEY_SECRET_NAME", "weather-api-key");
  private static final String WEATHER_API_KEY_SECRET_VERSION = System.getenv().getOrDefault("WEATHER_API_KEY_SECRET_VERSION", "latest");

  private static final Logger logger = Logger.getLogger(WeatherServlet.class.getName());

  MBeanServer server;
  ObjectName weatherON;
  ObjectInstance mbean;
  
  private SecretManagerServiceClient secretManagerClient;

  @Override
  public void init() {
    server = ManagementFactory.getPlatformMBeanServer();
    try {
      weatherON = new ObjectName("com.acme.modres.mbean:name=appInfo");
    } catch (MalformedObjectNameException e) {
      logger.log(Level.SEVERE, "Error creating MBean ObjectName", e);
      e.printStackTrace();
    }
    try {
      if (weatherON != null) {
        mbean = server.registerMBean(new AppInfo(), weatherON);
      }
    } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
      logger.log(Level.WARNING, "Error registering MBean", e);
      e.printStackTrace();
    }
    
    // Initialize Google Secret Manager client
    try {
      secretManagerClient = SecretManagerServiceClient.create();
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Failed to initialize Secret Manager client", e);
    }
  }

  @Override
  public void destroy() {
    if (mbean != null) {
      try {
        server.unregisterMBean(weatherON);
      } catch (MBeanRegistrationException | InstanceNotFoundException e) {
        logger.log(Level.WARNING, "Error unregistering MBean", e);
        e.printStackTrace();
      }
    }
    
    // Close Secret Manager client
    if (secretManagerClient != null) {
      try {
        secretManagerClient.close();
      } catch (Exception e) {
        logger.log(Level.WARNING, "Error closing Secret Manager client", e);
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
      logger.log(Level.WARNING, "Error getting MBean info", e);
      e.printStackTrace();
    }

    String city = request.getParameter("selectedCity");
    logger.log(Level.FINE, "requested city is " + city);

    // Retrieve API key from Google Secret Manager instead of environment variable
    String weatherAPIKey = getSecretFromSecretManager();
    String mockedKey = mockKey(weatherAPIKey);
    logger.log(Level.FINE, "weatherAPIKey is " + mockedKey);

    if (weatherAPIKey != null && weatherAPIKey.trim().length() > 0) {
      logger.info("weatherAPIKey is found, system will provide the real time weather data for the city " + city);
      getRealTimeWeatherData(city, weatherAPIKey, response);
    } else {
      logger.info(
          "weatherAPIKey is not found, will provide the weather data dated August 10th, 2018 for the city " + city);
      getDefaultWeatherData(city, response);
    }
  }
  
  /**
   * Retrieves secret from Google Secret Manager.
   * This provides centralized secret lifecycle management, access control, and audit logging.
   */
  private String getSecretFromSecretManager() {
    if (secretManagerClient == null) {
      logger.warning("Secret Manager client not initialized, falling back to environment variable");
      return System.getenv("WEATHER_API_KEY");
    }
    
    if (GCP_PROJECT_ID == null || GCP_PROJECT_ID.isEmpty()) {
      logger.warning("GCP_PROJECT_ID not set, falling back to environment variable");
      return System.getenv("WEATHER_API_KEY");
    }
    
    try {
      SecretVersionName secretVersionName = SecretVersionName.of(
          GCP_PROJECT_ID, 
          WEATHER_API_KEY_SECRET_NAME, 
          WEATHER_API_KEY_SECRET_VERSION
      );
      
      AccessSecretVersionResponse response = secretManagerClient.accessSecretVersion(secretVersionName);
      String secretValue = response.getPayload().getData().toStringUtf8();
      
      logger.info("Successfully retrieved API key from Secret Manager");
      return secretValue;
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to retrieve secret from Secret Manager, falling back to environment variable", e);
      return System.getenv("WEATHER_API_KEY");
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
      // Use try-with-resources for automatic resource management
      try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
           ServletOutputStream out = response.getOutputStream()) {
        
        String inputLine = null;
        StringBuffer responseStr = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
          responseStr.append(inputLine);
        }

        response.setContentType("application/json");
        out.print(responseStr.toString());
        logger.log(Level.FINE, "responseStr: " + responseStr);
      } catch (Exception e) {
        String errorMsg = "Problem occured when processing the weather server response.";
        ExceptionHandler.handleException(e, errorMsg, logger);
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

    // Use try-with-resources for automatic resource management
    try (ServletOutputStream out = response.getOutputStream()) {
      String responseStr = defaultWeatherData.getDefaultWeatherData();
      response.setContentType("application/json");
      out.print(responseStr.toString());
      logger.log(Level.FINEST, "responseStr: " + responseStr);
    } catch (Exception e) {
      String errorMsg = "Problem occured when getting the default weather data.";
      ExceptionHandler.handleException(e, errorMsg, logger);
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
}
