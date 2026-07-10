package com.example.feat1.DDD.order_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class OrderDomainException extends AppException {
  public static final String QUANTITY_INVALID = "ORDER_QUANTITY_INVALID";
  public static final String CART_LINE_NOT_FOUND = "ORDER_CART_LINE_NOT_FOUND";
  public static final String TABLE_REQUIRED = "ORDER_TABLE_REQUIRED";
  public static final String CART_TABLE_MISMATCH = "ORDER_CART_TABLE_MISMATCH";
  public static final String CART_EMPTY = "ORDER_CART_EMPTY";
  public static final String CART_TABLE_REQUIRED = "ORDER_CART_TABLE_REQUIRED";
  public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
  public static final String CANCEL_WINDOW_CLOSED = "ORDER_CANCEL_WINDOW_CLOSED";
  public static final String LINE_NOT_CANCELLABLE = "ORDER_LINE_NOT_CANCELLABLE";
  public static final String NO_CANCELLABLE_LINES = "ORDER_NO_CANCELLABLE_LINES";

  public OrderDomainException(String code, String message, HttpStatus status) {
    super(code, message, status);
  }

  public static OrderDomainException quantityInvalid() {
    return new OrderDomainException(
        QUANTITY_INVALID, "Cart item quantity must be positive", HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException cartLineNotFound() {
    return new OrderDomainException(
        CART_LINE_NOT_FOUND, "Cart line was not found", HttpStatus.NOT_FOUND);
  }

  public static OrderDomainException tableRequired() {
    return new OrderDomainException(TABLE_REQUIRED, "Table is required", HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException cartTableMismatch() {
    return new OrderDomainException(
        CART_TABLE_MISMATCH, "Cart already belongs to a different table", HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException cartEmpty() {
    return new OrderDomainException(CART_EMPTY, "Cart is empty", HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException cartTableRequired() {
    return new OrderDomainException(
        CART_TABLE_REQUIRED,
        "Cart table is required before submitting order",
        HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException orderNotFound() {
    return new OrderDomainException(ORDER_NOT_FOUND, "Order was not found", HttpStatus.NOT_FOUND);
  }

  public static OrderDomainException cancelWindowClosed() {
    return new OrderDomainException(
        CANCEL_WINDOW_CLOSED,
        "Order can no longer be cancelled once kitchen preparation has started",
        HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException lineNotCancellable() {
    return new OrderDomainException(
        LINE_NOT_CANCELLABLE, "Order line can no longer be cancelled", HttpStatus.BAD_REQUEST);
  }

  public static OrderDomainException noCancellableLines() {
    return new OrderDomainException(
        NO_CANCELLABLE_LINES, "No cancellable lines were provided", HttpStatus.BAD_REQUEST);
  }
}
