package com.example.feat1.DDD.inventory_context.domain.model;

/**
 * Types of stock movements recorded in the Inventory Context. Movements are immutable facts;
 * corrections are represented as additional movements rather than edits.
 */
public enum InventoryMovementType {
  /** Inbound stock received into the default location. */
  RECEIPT,
  /** Manual positive correction that increases stock-on-hand. */
  ADJUSTMENT_IN,
  /** Manual negative correction that decreases stock-on-hand. */
  ADJUSTMENT_OUT,
  /** Outbound removal for spoilage, breakage, or other loss. */
  WASTE,
  /**
   * Physical stock count that sets stock-on-hand to the counted quantity. This is the explicit
   * correction path allowed to reconcile stock to a counted value.
   */
  STOCK_COUNT,
  /** Outbound deduction settling a held reservation into an actual stock-on-hand decrease. */
  CONSUMPTION;

  public boolean isInbound() {
    return this == RECEIPT || this == ADJUSTMENT_IN;
  }

  public boolean isOutbound() {
    return this == ADJUSTMENT_OUT || this == WASTE || this == CONSUMPTION;
  }

  public boolean isCount() {
    return this == STOCK_COUNT;
  }
}
