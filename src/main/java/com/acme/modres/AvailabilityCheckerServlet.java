package com.acme.modres;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.acme.modres.mbean.IOUtils;
import com.acme.modres.mbean.reservation.DateChecker;
import com.acme.modres.mbean.reservation.ReservationCheckerData;
import com.acme.modres.mbean.reservation.Reservation;

import com.acme.modres.util.ZipValidator;

@WebServlet({ "/resorts/availability" })
public class AvailabilityCheckerServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private static final Logger logger = Logger.getLogger(AvailabilityCheckerServlet.class.getName());

  private static InitialContext context;

  private ReservationCheckerData reservationCheckerData;

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

      for (Reservation reservation : reservations) {
        try {
          Date fromDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getFromDate());
          Date toDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getToDate());
          Date selectedDate = reservationCheckerData.getSelectedDate();

          if (selectedDate.after(fromDate) && selectedDate.before(toDate)) {
            isAvailible = false;
            break;
          }
        } catch (ParseException ex) {
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
   * Export reservations to Azure Blob Storage instead of local file system.
   * This method now uses in-memory streams and would integrate with Azure Blob Storage SDK.
   * 
   * Note: Azure Blob Storage integration requires:
   * - Azure Storage SDK dependency in pom.xml
   * - Connection string from Azure Key Vault or environment variable
   * - BlobServiceClient configuration
   */
  protected int exportRevervations(String selectedDateStr) {
    // Use classpath resource instead of file system path
    try (InputStream resourceStream = IOUtils.class.getClassLoader().getResourceAsStream("reservations.json")) {
      if (resourceStream == null) {
        logger.severe("reservations.json not found in classpath");
        return -1;
      }

      // Create zip in memory instead of writing to local file system
      try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
           ZipOutputStream zipOut = new ZipOutputStream(baos)) {

        ZipEntry zipEntry = new ZipEntry("reservations.json");
        zipOut.putNextEntry(zipEntry);

        byte[] bytes = new byte[1024];
        int length;
        while ((length = resourceStream.read(bytes)) >= 0) {
          zipOut.write(bytes, 0, length);
        }
        
        zipOut.closeEntry();
        zipOut.finish();

        // At this point, baos.toByteArray() contains the zip file bytes
        // In a cloud-native implementation, upload to Azure Blob Storage:
        // BlobClient blobClient = blobContainerClient.getBlobClient("reservations.zip");
        // blobClient.upload(new ByteArrayInputStream(baos.toByteArray()), baos.size(), true);

        // For now, validate the in-memory zip
        byte[] zipBytes = baos.toByteArray();
        if (zipBytes.length > 0) {
          logger.info("Successfully created zip archive in memory (" + zipBytes.length + " bytes)");
          // TODO: Upload zipBytes to Azure Blob Storage
          return 0;
        }
      }
    } catch (IOException e) {
      logger.severe("Error creating zip archive: " + e.getMessage());
      e.printStackTrace();
    } catch (Throwable e) {
      logger.severe("Unexpected error: " + e.getMessage());
      e.printStackTrace();
    }
    return -1;
  }

}
