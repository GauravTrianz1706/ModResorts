package com.acme.modres;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.acme.modres.mbean.IOUtils;
import com.acme.modres.mbean.reservation.Reservation;
import com.acme.modres.mbean.reservation.ReservationCheckerData;
import com.acme.modres.util.ZipValidator;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@WebServlet({ "/resorts/availability" })
public class AvailabilityCheckerServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(AvailabilityCheckerServlet.class.getName());
  
  private static final String GCS_BUCKET_NAME = System.getenv().getOrDefault("GCS_BUCKET_NAME", "modresorts-config");
  
  private ReservationCheckerData reservationCheckerData;
  
  // Use ScheduledExecutorService instead of java.util.Timer for cloud-native scheduling
  private ScheduledExecutorService scheduledExecutor;
  
  private Storage storage;

  @Override
  public void init() {
    // Initialize Google Cloud Storage client
    try {
      storage = StorageOptions.getDefaultInstance().getService();
    } catch (Exception e) {
      logger.severe("Failed to initialize Google Cloud Storage: " + e.getMessage());
    }
    
    // Initialize scheduled executor for cloud-native task scheduling
    scheduledExecutor = Executors.newScheduledThreadPool(2);
    
    // Load reserved dates
    this.reservationCheckerData = new ReservationCheckerData(IOUtils.getReservationListFromConfig());
  }
  
  @Override
  public void destroy() {
    // Clean up scheduled executor
    if (scheduledExecutor != null && !scheduledExecutor.isShutdown()) {
      scheduledExecutor.shutdown();
    }
    super.destroy();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

    String methodName = "doGet";
    logger.entering(AvailabilityCheckerServlet.class.getName(), methodName);
    int statusCode = 200;

    String selectedDateStr = request.getParameter("date");
    boolean parsedDate = reservationCheckerData.setSelectedDate(selectedDateStr);
    if (!parsedDate || reservationCheckerData.getReservationList() == null) {
      statusCode = 500;
      reservationCheckerData.setAvailablility(false);
    } else {
      List<Reservation> reservations = reservationCheckerData.getReservationList().getReservations();
      boolean isAvailible = true;

      // Use UTC-based time comparison for cloud-native consistency
      for (Reservation reservation : reservations) {
        try {
          DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DATA_FORMAT);
          
          ZonedDateTime fromDate = ZonedDateTime.parse(reservation.getFromDate() + " 00:00:00", 
              DateTimeFormatter.ofPattern(Constants.DATA_FORMAT + " HH:mm:ss").withZone(ZoneOffset.UTC));
          ZonedDateTime toDate = ZonedDateTime.parse(reservation.getToDate() + " 00:00:00", 
              DateTimeFormatter.ofPattern(Constants.DATA_FORMAT + " HH:mm:ss").withZone(ZoneOffset.UTC));
          
          Instant selectedInstant = reservationCheckerData.getSelectedDate().toInstant();
          ZonedDateTime selectedDate = ZonedDateTime.ofInstant(selectedInstant, ZoneOffset.UTC);

          if (selectedDate.isAfter(fromDate) && selectedDate.isBefore(toDate)) {
            isAvailible = false;
            break;
          }
        } catch (Exception ex) {
          logger.warning("Error parsing reservation dates: " + ex.getMessage());
          ex.printStackTrace();
        }
      }

      reservationCheckerData.setAvailablility(isAvailible);

      // Adjust the status code based on availability
      if (!isAvailible) {
        statusCode = 201;
      }
    }

    // Send the response with try-with-resources for automatic resource management
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    try (PrintWriter out = response.getWriter()) {
      out.print("{\"availability\": \"" + String.valueOf(reservationCheckerData.isAvailible()) + "\"}");
    }
    response.setStatus(statusCode);
  }

  /**
   * Returns the weather information for a given city
   */
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    doGet(request, response);
  }

  /**
   * Exports reservations to Google Cloud Storage instead of local file system.
   * This ensures data persistence across container restarts and scaling events.
   */
  protected int exportRevervations(String selectedDateStr) {
    if (storage == null) {
      logger.severe("Google Cloud Storage client not initialized");
      return -1;
    }
    
    // Use try-with-resources for automatic resource management
    try (InputStream fileStream = IOUtils.getResourceAsStream("reservations.json");
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ZipOutputStream zipOut = new ZipOutputStream(baos)) {
      
      if (fileStream == null) {
        logger.severe("reservations.json not found");
        return -1;
      }
      
      // Create zip entry
      ZipEntry zipEntry = new ZipEntry("reservations.json");
      zipOut.putNextEntry(zipEntry);

      // Copy file content to zip
      byte[] bytes = new byte[1024];
      int length;
      while ((length = fileStream.read(bytes)) >= 0) {
        zipOut.write(bytes, 0, length);
      }
      
      zipOut.closeEntry();
      zipOut.finish();
      
      // Upload to Google Cloud Storage
      byte[] zipContent = baos.toByteArray();
      String gcsPath = "exports/reservations_" + System.currentTimeMillis() + ".zip";
      
      BlobInfo blobInfo = BlobInfo.newBuilder(GCS_BUCKET_NAME, gcsPath).build();
      storage.create(blobInfo, zipContent);
      
      logger.info("Successfully exported reservations to GCS: " + gcsPath);
      return 0;
      
    } catch (IOException e) {
      logger.severe("Error exporting reservations: " + e.getMessage());
      e.printStackTrace();
      return -1;
    } catch (Throwable e) {
      logger.severe("Unexpected error exporting reservations: " + e.getMessage());
      e.printStackTrace();
      return -1;
    }
  }
}
