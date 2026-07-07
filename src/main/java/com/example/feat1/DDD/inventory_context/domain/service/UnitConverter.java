package com.example.feat1.DDD.inventory_context.domain.service;

import com.example.feat1.DDD.inventory_context.domain.model.InventoryDomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic, explicit unit conversion for the Inventory Context. Shared by costing and stock
 * management so both use identical normalization and conversion factors (Phase 13 behavior).
 */
public final class UnitConverter {
  public static final int QUANTITY_SCALE = 6;

  private UnitConverter() {}

  public static String normalizeUnit(String unit) {
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("Unit is required");
    }
    String normalized = unit.trim().toLowerCase();
    if (normalized.equals("piece") || normalized.equals("pieces")) {
      return "pcs";
    }
    return normalized;
  }

  public static BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
    String from = normalizeUnit(fromUnit);
    String to = normalizeUnit(toUnit);
    if (from.equals(to)) {
      return quantity.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }
    return quantity
        .multiply(conversionFactor(from, to))
        .setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal conversionFactor(String from, String to) {
    if (from.equals("kg") && to.equals("g")) {
      return BigDecimal.valueOf(1000);
    }
    if (from.equals("g") && to.equals("kg")) {
      return BigDecimal.valueOf(0.001);
    }
    if (from.equals("l") && to.equals("ml")) {
      return BigDecimal.valueOf(1000);
    }
    if (from.equals("ml") && to.equals("l")) {
      return BigDecimal.valueOf(0.001);
    }
    throw InventoryDomainException.unitConversionUnsupported();
  }
}
