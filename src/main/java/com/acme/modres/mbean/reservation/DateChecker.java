package com.acme.modres.mbean.reservation;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.acme.modres.Constants;

/**
 * Cloud-native date checker using UTC timestamps for consistent behavior
 * across distributed cloud environments and multiple regions.
 */
public class DateChecker implements Runnable {
  ReservationCheckerData data;
  List<Reservation> reservations;

  public DateChecker(ReservationCheckerData data) {
    this.data = data;
    this.reservations = data.getReservationList().getReservations();
  }

  public void run() {
    for (int i = 0; i < reservations.size(); i++) {
      Reservation reservation = reservations.get(i);
      
      // Use UTC-based time comparison for cloud-native consistency
      try {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Constants.DATA_FORMAT + " HH:mm:ss")
            .withZone(ZoneOffset.UTC);
        
        ZonedDateTime fromDate = ZonedDateTime.parse(reservation.getFromDate() + " 00:00:00", formatter);
        ZonedDateTime toDate = ZonedDateTime.parse(reservation.getToDate() + " 00:00:00", formatter);
        
        Instant selectedInstant = data.getSelectedDate().toInstant();
        ZonedDateTime selectedDate = ZonedDateTime.ofInstant(selectedInstant, ZoneOffset.UTC);
        
        if (selectedDate.isAfter(fromDate) && selectedDate.isBefore(toDate)) {
          data.setAvailablility(false);
          return;
        }
      } catch (Exception ex) {
        System.err.println("Error parsing reservation dates: " + ex.getMessage());
        ex.printStackTrace();
      }
    }
    data.setAvailablility(true);
  }
}
