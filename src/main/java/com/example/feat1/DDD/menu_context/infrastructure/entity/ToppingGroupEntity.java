package com.example.feat1.DDD.menu_context.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "topping_groups")
public class ToppingGroupEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "dish_id", nullable = false)
  private DishEntity dish;

  @Column(nullable = false)
  private String name;

  @Column(name = "min_selections", nullable = false)
  private int minSelections;

  @Column(name = "max_selections", nullable = false)
  private int maxSelections;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;
}
