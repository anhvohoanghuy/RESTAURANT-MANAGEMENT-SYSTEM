package com.example.feat1.DDD.kitchen_context.application;

import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenProcessedEventEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemToppingSnapshot;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenProcessedEventRepository;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketRepository;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedLine;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedTopping;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer half of D-01: turns exactly one {@link OrderConfirmedEvent} into exactly one {@link
 * KitchenTicketEntity} with all {@link KitchenTicketItemEntity} rows built up-front from the
 * event's line manifest, without any cross-context lookup. Idempotent via the {@code (eventId,
 * consumerName)} ledger — replay of the same event creates no duplicate ticket (T-17-06); a
 * concurrent duplicate ledger insert is swallowed rather than propagated.
 *
 * <p>Never add items to the ticket after creation (Pitfall 4): downstream item-count invariants
 * depend on item count equalling the manifest's line count from the single save.
 */
@Service
@RequiredArgsConstructor
public class KitchenTicketCreationService {

  /** Ledger consumer identity for the kitchen-side OrderConfirmed handler. */
  static final String CONSUMER_NAME = "kitchen-order-confirmed";

  private final KitchenProcessedEventRepository processedEventRepository;
  private final KitchenTicketRepository kitchenTicketRepository;

  @Transactional
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    // (1) Idempotency: fast pre-check, then insert + immediate flush as the authoritative guard.
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }
    try {
      KitchenProcessedEventEntity ledger = new KitchenProcessedEventEntity();
      ledger.setEventId(event.eventId());
      ledger.setConsumerName(CONSUMER_NAME);
      ledger.setProcessedAt(Instant.now());
      processedEventRepository.saveAndFlush(ledger);
    } catch (DataIntegrityViolationException duplicate) {
      // Concurrent delivery inserted the same (eventId, consumer) first — treat as a replay.
      return;
    }

    // (2) Build the ticket and ALL its items in a single pass — never append items later.
    KitchenTicketEntity ticket = new KitchenTicketEntity();
    ticket.setOrderId(event.orderId());
    ticket.setCreatedAt(Instant.now());
    for (OrderConfirmedLine line : event.lines()) {
      ticket.getItems().add(toItem(ticket, line));
    }

    kitchenTicketRepository.save(ticket);
  }

  private KitchenTicketItemEntity toItem(KitchenTicketEntity ticket, OrderConfirmedLine line) {
    KitchenTicketItemEntity item = new KitchenTicketItemEntity();
    item.setTicket(ticket);
    item.setOrderLineId(line.lineId());
    item.setDishId(line.dishId());
    item.setDishName(line.dishName());
    item.setQuantity(line.quantity());
    for (OrderConfirmedTopping topping : line.selectedToppings()) {
      item.getSelectedToppings().add(toToppingSnapshot(topping));
    }
    return item;
  }

  private KitchenTicketItemToppingSnapshot toToppingSnapshot(OrderConfirmedTopping topping) {
    KitchenTicketItemToppingSnapshot snapshot = new KitchenTicketItemToppingSnapshot();
    snapshot.setToppingGroupId(topping.toppingGroupId());
    snapshot.setToppingGroupName(topping.toppingGroupName());
    snapshot.setToppingOptionId(topping.toppingOptionId());
    snapshot.setToppingOptionName(topping.toppingOptionName());
    return snapshot;
  }
}
