package com.acme.modres.mbean.reservation;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;

import com.acme.modres.Constants;

/**
 * Cloud-native reservation checker using UTC timestamps for consistent behavior
 * across distributed cloud environments and multiple regions.
 */
public class ReservationCheckerData {
  private ReservationList reservations;
  private Date selectedDate;
  private boolean available; // changed from Boolean to boolean

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
   * Sets the selected date using UTC timezone for cloud-native consistency.
   * This eliminates server-local timezone dependencies.
   */
  public boolean setSelectedDate(String dateStr) {
    try {
      SimpleDateFormat sdf = new SimpleDateFormat(Constants.DATA_FORMAT);
      // Use UTC timezone to eliminate local timezone dependencies
      sdf.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
      selectedDate = sdf.parse(dateStr);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  public boolean isAvailible() {
    return available;
  }

  public void setAvailablility(boolean available) { // fix parameter type
    this.available = available;
  }
}
