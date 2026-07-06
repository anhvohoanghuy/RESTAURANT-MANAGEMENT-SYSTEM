package com.example.feat1.DDD.table_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class TableDomainException extends AppException {
  public static final String TABLE_NOT_ORDERABLE = "TABLE_NOT_ORDERABLE";
  public static final String TABLE_AREA_NOT_ORDERABLE = "TABLE_AREA_NOT_ORDERABLE";
  public static final String TABLE_SESSION_NOT_FOUND = "TABLE_SESSION_NOT_FOUND";
  public static final String TABLE_SESSION_ALREADY_OPEN = "TABLE_SESSION_ALREADY_OPEN";
  public static final String TABLE_SESSION_NOT_OPEN = "TABLE_SESSION_NOT_OPEN";
  public static final String TABLE_OCCUPANCY_TRANSITION_INVALID =
      "TABLE_OCCUPANCY_TRANSITION_INVALID";
  public static final String TABLE_RESERVATION_NOT_FOUND = "TABLE_RESERVATION_NOT_FOUND";
  public static final String TABLE_RESERVATION_OVERLAP = "TABLE_RESERVATION_OVERLAP";
  public static final String TABLE_RESERVATION_STATUS_INVALID = "TABLE_RESERVATION_STATUS_INVALID";
  public static final String TABLE_AVAILABILITY_WINDOW_INVALID =
      "TABLE_AVAILABILITY_WINDOW_INVALID";

  public TableDomainException(String code, String message) {
    super(code, message, HttpStatus.BAD_REQUEST);
  }

  public static TableDomainException tableNotOrderable() {
    return new TableDomainException(TABLE_NOT_ORDERABLE, "Table is not orderable");
  }

  public static TableDomainException tableAreaNotOrderable() {
    return new TableDomainException(TABLE_AREA_NOT_ORDERABLE, "Table area is not orderable");
  }

  public static TableDomainException tableSessionNotFound() {
    return new TableDomainException(TABLE_SESSION_NOT_FOUND, "Table session was not found");
  }

  public static TableDomainException tableSessionAlreadyOpen() {
    return new TableDomainException(
        TABLE_SESSION_ALREADY_OPEN, "Table already has an open session");
  }

  public static TableDomainException tableSessionNotOpen() {
    return new TableDomainException(TABLE_SESSION_NOT_OPEN, "Table session is not open");
  }

  public static TableDomainException occupancyTransitionInvalid() {
    return new TableDomainException(
        TABLE_OCCUPANCY_TRANSITION_INVALID, "Table occupancy transition is invalid");
  }

  public static TableDomainException reservationNotFound() {
    return new TableDomainException(TABLE_RESERVATION_NOT_FOUND, "Table reservation was not found");
  }

  public static TableDomainException reservationOverlap() {
    return new TableDomainException(
        TABLE_RESERVATION_OVERLAP, "Reservation overlaps an active reservation");
  }

  public static TableDomainException reservationStatusInvalid() {
    return new TableDomainException(
        TABLE_RESERVATION_STATUS_INVALID, "Reservation status transition is invalid");
  }

  public static TableDomainException availabilityWindowInvalid() {
    return new TableDomainException(
        TABLE_AVAILABILITY_WINDOW_INVALID, "Availability time window is invalid");
  }
}
