package com.acme.modres;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
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

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@WebServlet({ "/resorts/availability" })
public class AvailabilityCheckerServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(AvailabilityCheckerServlet.class.getName());

  private ReservationCheckerData reservationCheckerData;
  
  // S3 configuration from environment variables
  private static final String S3_BUCKET_NAME = System.getenv().getOrDefault("S3_BUCKET_NAME", "modresorts-data");
  private static final String AWS_REGION = System.getenv().getOrDefault("AWS_REGION", "us-east-1");

  @Override
  public void init() {
    // load reserved dates
    this.reservationCheckerData = new ReservationCheckerData(IOUtils.getReservationListFromConfig());
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

      DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DATA_FORMAT);
      
      for (Reservation reservation : reservations) {
        try {
          LocalDate fromDate = LocalDate.parse(reservation.getFromDate(), formatter);
          LocalDate toDate = LocalDate.parse(reservation.getToDate(), formatter);
          LocalDate selectedDate = reservationCheckerData.getSelectedDate();

          if (selectedDate.isAfter(fromDate) && selectedDate.isBefore(toDate)) {
            isAvailible = false;
            break;
          }
        } catch (DateTimeParseException ex) {
          logger.severe("Failed to parse date: " + ex.getMessage());
          ex.printStackTrace();
        }
      }

      reservationCheckerData.setAvailablility(isAvailible);

      // Adjust the status code based on availability
      if (!isAvailible) {
        statusCode = 201;
      }
    }

    // Send the response
    PrintWriter out = response.getWriter();
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");
    out.print("{\"availability\": \"" + String.valueOf(reservationCheckerData.isAvailible()) + "\"}");
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
   * Export reservations to S3 instead of local file system
   */
  protected int exportRevervations(String selectedDateStr) {
    // Use try-with-resources to ensure proper resource cleanup
    try (S3Client s3Client = S3Client.builder()
        .region(Region.of(AWS_REGION))
        .build()) {
      
      // Read reservation data from classpath
      try (InputStream reservationStream = getClass().getClassLoader().getResourceAsStream("reservations.json")) {
        if (reservationStream == null) {
          logger.severe("reservations.json not found in classpath");
          return -1;
        }
        
        // Create zip in memory using try-with-resources
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zipOut = new ZipOutputStream(baos)) {
          
          ZipEntry zipEntry = new ZipEntry("reservations.json");
          zipOut.putNextEntry(zipEntry);

          byte[] bytes = new byte[1024];
          int length;
          while ((length = reservationStream.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
          }
          
          zipOut.closeEntry();
          zipOut.finish();
          
          // Upload to S3
          String s3Key = "reservations/reservations-" + Instant.now().toEpochMilli() + ".zip";
          byte[] zipData = baos.toByteArray();
          
          PutObjectRequest putObjectRequest = PutObjectRequest.builder()
              .bucket(S3_BUCKET_NAME)
              .key(s3Key)
              .contentType("application/zip")
              .build();
          
          s3Client.putObject(putObjectRequest, RequestBody.fromBytes(zipData));
          
          logger.info("Successfully uploaded reservations to S3: " + s3Key);
          
          // Verify zip data
          try (ByteArrayInputStream bais = new ByteArrayInputStream(zipData)) {
            // Basic validation - check if zip data is not empty
            if (zipData.length > 0) {
              return 0;
            }
          }
        }
      }
    } catch (Exception e) {
      logger.severe("Failed to export reservations to S3: " + e.getMessage());
      e.printStackTrace();
    }
    return -1;
  }

}
