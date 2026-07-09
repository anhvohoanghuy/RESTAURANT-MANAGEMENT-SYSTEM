package com.example.feat1.DDD.kitchen_context.infrastructure.entity;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Full JPA entity (not {@code @Embeddable}/{@code @ElementCollection}) so each item is
 * independently lockable and URL-addressable by its own {@code id} (D-02, Open Question #2
 * resolved). {@code orderLineId} is the source order line, reused as the {@code SettleTriggerEvent}
 * {@code orderLineId} when this item transitions QUEUED -> PREPARING.
 */
@Getter
@Setter
@Entity
@Table(name = "kitchen_ticket_items")
public class KitchenTicketItemEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "ticket_id", nullable = false)
  private KitchenTicketEntity ticket;

  @Column(name = "order_line_id", nullable = false)
  private UUID orderLineId;

  @Column(name = "dish_id", nullable = false)
  private UUID dishId;

  @Column(name = "dish_name", nullable = false)
  private String dishName;

  @Column(nullable = false)
  private int quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private KitchenItemStatus status = KitchenItemStatus.QUEUED;

  /** Who advanced this item to its current status (K-WR-02 accountability). Null until advanced. */
  @Column(name = "advanced_by")
  private UUID advancedBy;

  /** When this item was last advanced (K-WR-02 accountability). Null until advanced. */
  @Column(name = "advanced_at")
  private Instant advancedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "kitchen_ticket_item_toppings",
      joinColumns = @JoinColumn(name = "item_id"))
  @OrderColumn(name = "sort_order")
  private List<KitchenTicketItemToppingSnapshot> selectedToppings = new ArrayList<>();
}
