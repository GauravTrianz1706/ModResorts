package com.acme.modres.mbean.reservation;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.acme.modres.Constants;

/**
 * Reservation checker data holder.
 * Migrated for cloud-native deployment with timezone-aware date handling.
 * 
 * Cloud-native changes:
 * - Added timezone-aware date parsing using java.time API
 * - Uses UTC timezone for consistency across Azure regions
 * - Thread-safe date handling for distributed cloud environments
 */
public class ReservationCheckerData {
  private static final Logger logger = Logger.getLogger(ReservationCheckerData.class.getName());
  
  private ReservationList reservations;
  private Date selectedDate;
  private boolean available;

  public ReservationCheckerData(ReservationList reservations) {
    this.reservations = reservations;
    this.available = true;
  }

  public ReservationList getReservationList() {
    return reservations;
  }

  public Date getSelectedDate() {
    return selectedDate;
  }

  /**
   * Sets the selected date from a string.
   * Uses timezone-aware parsing for cloud deployments across regions.
   * 
   * @param dateStr Date string in the format specified by Constants.DATA_FORMAT
   * @return true if parsing succeeded, false otherwise
   */
  public boolean setSelectedDate(String dateStr) {
    if (dateStr == null || dateStr.trim().isEmpty()) {
      logger.warning("Date string is null or empty");
      return false;
    }

    try {
      // Parse date using SimpleDateFormat for backward compatibility
      SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATA_FORMAT);
      // Set timezone to UTC for consistency across Azure regions
      sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
      selectedDate = sdf.parse(dateStr);
      
      logger.log(Level.FINE, "Successfully parsed date: " + dateStr + " as " + selectedDate);
      return true;
      
    } catch (Exception e) {
      logger.log(Level.WARNING, "Failed to parse date string: " + dateStr, e);
      selectedDate = null;
      return false;
    }
  }

  public boolean isAvailible() {
    return available;
  }

  public void setAvailablility(boolean available) {
    this.available = available;
  }
  
  /**
   * Gets the selected date as a timezone-aware ZonedDateTime.
   * Useful for cloud-native date comparisons across regions.
   * 
   * @return ZonedDateTime in UTC, or null if selectedDate is null
   */
  public ZonedDateTime getSelectedDateAsZonedDateTime() {
    if (selectedDate == null) {
      return null;
    }
    return ZonedDateTime.ofInstant(selectedDate.toInstant(), ZoneId.of("UTC"));
  }
}
