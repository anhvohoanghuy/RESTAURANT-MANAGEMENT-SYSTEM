package com.example.feat1.DDD.order_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Embeddable
public class OrderLineToppingSnapshot {
  @Column(name = "topping_group_id", nullable = false)
  private UUID toppingGroupId;

  @Column(name = "topping_group_name", nullable = false)
  private String toppingGroupName;

  @Column(name = "topping_option_id", nullable = false)
  private UUID toppingOptionId;

  @Column(name = "topping_option_name", nullable = false)
  private String toppingOptionName;

  @Column(name = "additional_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal additionalPrice;
}
