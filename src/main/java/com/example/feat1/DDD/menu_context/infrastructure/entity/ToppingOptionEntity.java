package com.example.feat1.DDD.menu_context.infrastructure.entity;

import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "topping_options")
public class ToppingOptionEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "topping_group_id", nullable = false)
  private ToppingGroupEntity toppingGroup;

  @Column(nullable = false)
  private String name;

  @Column(name = "additional_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal additionalPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MenuStatus status = MenuStatus.ACTIVE;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
