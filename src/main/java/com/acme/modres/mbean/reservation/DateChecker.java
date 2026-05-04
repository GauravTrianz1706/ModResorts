package com.acme.modres.mbean.reservation;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.acme.modres.Constants;

public class DateChecker implements Runnable {
  private static final Logger logger = Logger.getLogger(DateChecker.class.getName());
  
  ReservationCheckerData data;
  List<Reservation> reservations;

  public DateChecker(ReservationCheckerData data) {
    this.data = data;
    this.reservations = data.getReservationList().getReservations();
  }

  public void run() {
    for (int i = 0; i < reservations.size(); i++) {
      Reservation reservation = reservations.get(i);
      Date selectedDate = data.getSelectedDate();

      try {
        Date fromDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getFromDate());
        Date toDate = new SimpleDateFormat(Constants.DATA_FORMAT).parse(reservation.getToDate());
        if (selectedDate.after(fromDate) && selectedDate.before(toDate)) {
          data.setAvailablility(false);
          break;
        }
      } catch (ParseException ex) {
        logger.log(Level.SEVERE, "Error parsing date", ex);
      }
    }
    data.setAvailablility(true);
  }
}
