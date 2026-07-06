package com.example.feat1.DDD.inventory_context.domain.model;

import com.example.feat1.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class InventoryDomainException extends AppException {
  public static final String INGREDIENT_NOT_FOUND = "INVENTORY_INGREDIENT_NOT_FOUND";
  public static final String INGREDIENT_NOT_ACTIVE = "INVENTORY_INGREDIENT_NOT_ACTIVE";
  public static final String COST_INVALID = "INVENTORY_COST_INVALID";
  public static final String COST_NOT_FOUND = "INVENTORY_COST_NOT_FOUND";
  public static final String UNIT_CONVERSION_UNSUPPORTED = "INVENTORY_UNIT_CONVERSION_UNSUPPORTED";

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
}
