package com.example.feat1.DDD.inventory_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class InventoryDomainException extends AppException {
  public static final String INGREDIENT_NOT_FOUND = "INVENTORY_INGREDIENT_NOT_FOUND";
  public static final String INGREDIENT_NOT_ACTIVE = "INVENTORY_INGREDIENT_NOT_ACTIVE";
  public static final String COST_INVALID = "INVENTORY_COST_INVALID";
  public static final String COST_NOT_FOUND = "INVENTORY_COST_NOT_FOUND";
  public static final String UNIT_CONVERSION_UNSUPPORTED = "INVENTORY_UNIT_CONVERSION_UNSUPPORTED";
  public static final String MOVEMENT_INVALID = "INVENTORY_MOVEMENT_INVALID";
  public static final String MOVEMENT_QUANTITY_INVALID = "INVENTORY_MOVEMENT_QUANTITY_INVALID";
  public static final String STOCK_INSUFFICIENT = "INVENTORY_STOCK_INSUFFICIENT";
  public static final String STOCK_NOT_FOUND = "INVENTORY_STOCK_NOT_FOUND";
  public static final String SETTLEMENT_RESERVATION_MISSING =
      "INVENTORY_SETTLEMENT_RESERVATION_MISSING";
  public static final String SETTLEMENT_ORDER_LINE_MISSING =
      "INVENTORY_SETTLEMENT_ORDER_LINE_MISSING";
  public static final String RELEASE_RESERVATION_MISSING = "INVENTORY_RELEASE_RESERVATION_MISSING";
  public static final String RELEASE_ORDER_LINE_MISSING = "INVENTORY_RELEASE_ORDER_LINE_MISSING";

  private InventoryDomainException(String code, String message, HttpStatus status) {
    super(code, message, status);
  }

  public static InventoryDomainException ingredientNotFound() {
    return new InventoryDomainException(
        INGREDIENT_NOT_FOUND, "Ingredient was not found", HttpStatus.NOT_FOUND);
  }

  public static InventoryDomainException ingredientNotActive() {
    return new InventoryDomainException(
        INGREDIENT_NOT_ACTIVE, "Ingredient is not active", HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException costInvalid() {
    return new InventoryDomainException(
        COST_INVALID, "Ingredient cost is invalid", HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException costNotFound() {
    return new InventoryDomainException(
        COST_NOT_FOUND, "Ingredient cost was not found", HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException unitConversionUnsupported() {
    return new InventoryDomainException(
        UNIT_CONVERSION_UNSUPPORTED, "Unit conversion is unsupported", HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException movementInvalid(String message) {
    return new InventoryDomainException(MOVEMENT_INVALID, message, HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException movementQuantityInvalid() {
    return new InventoryDomainException(
        MOVEMENT_QUANTITY_INVALID, "Movement quantity must be positive", HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException stockInsufficient() {
    return new InventoryDomainException(
        STOCK_INSUFFICIENT,
        "Stock-on-hand is insufficient for this outbound movement",
        HttpStatus.BAD_REQUEST);
  }

  public static InventoryDomainException stockNotFound() {
    return new InventoryDomainException(
        STOCK_NOT_FOUND, "Stock balance was not found", HttpStatus.NOT_FOUND);
  }

  /**
   * Thrown when a settle-trigger arrives but no reservation exists for the order. Left OUT of the
   * Kafka non-retryable set so a transient ordering race (settle before reserve commits) is retried
   * and only lands on the DLT after backoff exhaustion (D-05).
   */
  public static InventoryDomainException settlementReservationMissing(java.util.UUID orderId) {
    return new InventoryDomainException(
        SETTLEMENT_RESERVATION_MISSING,
        "No stock reservation found to settle for order " + orderId,
        HttpStatus.CONFLICT);
  }

  /** Thrown when the order line to settle cannot be re-resolved (missing order-line data). */
  public static InventoryDomainException settlementOrderLineMissing(
      java.util.UUID orderId, java.util.UUID orderLineId) {
    return new InventoryDomainException(
        SETTLEMENT_ORDER_LINE_MISSING,
        "Order line " + orderLineId + " for order " + orderId + " was not found to settle",
        HttpStatus.CONFLICT);
  }

  /**
   * Thrown when an OrderCancelled trigger arrives but no reservation exists for the order. Left OUT
   * of the Kafka non-retryable set so a transient ordering race (cancel before reserve commits) is
   * retried and only lands on the DLT after backoff exhaustion (mirrors D-05).
   */
  public static InventoryDomainException releaseReservationMissing(java.util.UUID orderId) {
    return new InventoryDomainException(
        RELEASE_RESERVATION_MISSING,
        "No stock reservation found to release for order " + orderId,
        HttpStatus.CONFLICT);
  }

  /** Thrown when the order line to release cannot be re-resolved (missing order-line data). */
  public static InventoryDomainException releaseOrderLineMissing(
      java.util.UUID orderId, java.util.UUID orderLineId) {
    return new InventoryDomainException(
        RELEASE_ORDER_LINE_MISSING,
        "Order line " + orderLineId + " for order " + orderId + " was not found to release",
        HttpStatus.CONFLICT);
  }
}
