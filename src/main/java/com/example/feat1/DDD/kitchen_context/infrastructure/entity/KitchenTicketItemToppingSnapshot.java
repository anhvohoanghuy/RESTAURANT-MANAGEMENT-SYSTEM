package com.example.feat1.DDD.kitchen_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Kitchen's own snapshot of a selected topping's identity (name/id only — no price; kitchen does
 * not need pricing data). Mirrors {@code OrderLineToppingSnapshot} minus the price column.
 */
@Getter
@Setter
@Embeddable
public class KitchenTicketItemToppingSnapshot {
  @Column(name = "topping_group_id", nullable = false)
  private UUID toppingGroupId;

  @Column(name = "topping_group_name", nullable = false)
  private String toppingGroupName;

  @Column(name = "topping_option_id", nullable = false)
  private UUID toppingOptionId;

  @Column(name = "topping_option_name", nullable = false)
  private String toppingOptionName;
}
