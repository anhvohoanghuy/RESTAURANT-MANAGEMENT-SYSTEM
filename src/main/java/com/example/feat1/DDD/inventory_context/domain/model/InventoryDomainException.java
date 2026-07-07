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
}
