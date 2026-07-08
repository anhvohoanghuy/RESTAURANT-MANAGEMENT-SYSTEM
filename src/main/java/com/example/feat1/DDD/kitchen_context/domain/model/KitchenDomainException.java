package com.example.feat1.DDD.kitchen_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class KitchenDomainException extends AppException {
  public static final String KITCHEN_TICKET_NOT_FOUND = "KITCHEN_TICKET_NOT_FOUND";
  public static final String KITCHEN_ITEM_NOT_FOUND = "KITCHEN_ITEM_NOT_FOUND";
  public static final String KITCHEN_TRANSITION_INVALID = "KITCHEN_TRANSITION_INVALID";

  public KitchenDomainException(String code, String message, HttpStatus status) {
    super(code, message, status);
  }

  public static KitchenDomainException ticketNotFound() {
    return new KitchenDomainException(
        KITCHEN_TICKET_NOT_FOUND, "Kitchen ticket was not found", HttpStatus.NOT_FOUND);
  }

  public static KitchenDomainException itemNotFound() {
    return new KitchenDomainException(
        KITCHEN_ITEM_NOT_FOUND, "Kitchen ticket item was not found", HttpStatus.NOT_FOUND);
  }

  public static KitchenDomainException transitionInvalid() {
    return new KitchenDomainException(
        KITCHEN_TRANSITION_INVALID,
        "Kitchen item status transition is invalid",
        HttpStatus.BAD_REQUEST);
  }
}
