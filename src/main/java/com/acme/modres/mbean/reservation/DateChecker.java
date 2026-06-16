package com.acme.modres.mbean.reservation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.acme.modres.Constants;

/**
 * Date checker for reservation availability.
 * Migrated for cloud-native deployment with timezone-aware date handling.
 * 
 * Cloud-native changes:
 * - Removed dependency on java.util.Timer (not cloud-friendly)
 * - Added timezone-aware date handling using java.time API
 * - Ready for Azure Service Bus scheduled messages or Azure Functions timer triggers
 * 
 * For distributed scheduling in Azure:
 * 1. Azure Service Bus Scheduled Messages: Schedule message delivery for future execution
 * 2. Azure Functions Timer Trigger: CRON-based scheduling
 * 3. Azure Logic Apps: Workflow-based scheduling
 * 
 * Example Azure Functions Timer Trigger:
 * @FunctionName("DateChecker")
 * public void run(
 *   @TimerTrigger(name = "timerInfo", schedule = "0 0 * * * *") String timerInfo,
 *   ExecutionContext context
 * ) {
 *   // Execute date checking logic
 * }
 */
public class DateChecker implements Runnable {
  private static final Logger logger = Logger.getLogger(DateChecker.class.getName());
  
  ReservationCheckerData data;
  List<Reservation> reservations;

  public DateChecker(ReservationCheckerData data) {
    this.data = data;
    this.reservations = data.getReservationList().getReservations();
  }

  /**
   * Checks reservation availability for the selected date.
   * Uses timezone-aware date comparison for cloud deployments across regions.
   */
  public void run() {
    try {
      Date selectedDate = data.getSelectedDate();
      if (selectedDate == null) {
        logger.warning("Selected date is null, cannot check availability");
        data.setAvailablility(false);
        return;
      }

      // Convert to ZonedDateTime for timezone-aware comparison
      // In Azure, use UTC for consistency across regions
      ZonedDateTime selectedZdt = ZonedDateTime.ofInstant(
        selectedDate.toInstant(), 
        ZoneId.of("UTC")
      );

      boolean isAvailable = true;

      for (Reservation reservation : reservations) {
        try {
          Date fromDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getFromDate());
          Date toDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getToDate());
          
          // Convert to ZonedDateTime for proper comparison
          ZonedDateTime fromZdt = ZonedDateTime.ofInstant(fromDate.toInstant(), ZoneId.of("UTC"));
          ZonedDateTime toZdt = ZonedDateTime.ofInstant(toDate.toInstant(), ZoneId.of("UTC"));

          // Check if selected date falls within reservation period
          if (selectedZdt.isAfter(fromZdt) && selectedZdt.isBefore(toZdt)) {
            isAvailable = false;
            logger.log(Level.FINE, "Date " + selectedDate + " is not available (reserved from " + 
                      reservation.getFromDate() + " to " + reservation.getToDate() + ")");
            break;
          }
        } catch (ParseException ex) {
          logger.log(Level.SEVERE, "Error parsing reservation dates for reservation: " + reservation, ex);
          // Continue checking other reservations
        }
      }

      data.setAvailablility(isAvailable);
      
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Error checking date availability", e);
      data.setAvailablility(false);
    }
  }
  
  /**
   * Execute the date check synchronously.
   * For cloud environments, this can be invoked by:
   * - Azure Service Bus message handler
   * - Azure Functions timer trigger
   * - Spring @Scheduled annotation (if using Spring Boot)
   */
  public void checkAvailability() {
    run();
  }
}
