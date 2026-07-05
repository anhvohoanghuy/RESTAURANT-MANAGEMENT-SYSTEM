package com.example.feat1.DDD.order_context.infrastructure.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_lines")
public class OrderLineEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private OrderEntity order;

  @Column(name = "dish_id", nullable = false)
  private UUID dishId;

  @Column(name = "topping_key", nullable = false, length = 2000)
  private String toppingKey = "";

  @Column(name = "dish_name", nullable = false)
  private String dishName;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_line_toppings", joinColumns = @JoinColumn(name = "line_id"))
  @OrderColumn(name = "sort_order")
  private List<OrderLineToppingSnapshot> selectedToppings = new ArrayList<>();

  @Column(name = "toppings_total", nullable = false, precision = 12, scale = 2)
  private BigDecimal toppingsTotal;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
  private BigDecimal lineTotal;
}
