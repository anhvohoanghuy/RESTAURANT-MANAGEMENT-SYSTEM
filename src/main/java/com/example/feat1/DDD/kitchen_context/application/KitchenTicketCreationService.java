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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(KitchenTicketCreationService.class);

  /** Ledger consumer identity for the kitchen-side OrderConfirmed handler. */
  static final String CONSUMER_NAME = "kitchen-order-confirmed";

  private final KitchenProcessedEventRepository processedEventRepository;
  private final KitchenTicketRepository kitchenTicketRepository;

  @Transactional
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    // (1) Idempotency fast pre-check: absorb an already-recorded replay cheaply. The authoritative
    // ledger insert happens at the END of this method, in this same transaction (I-WR-01 / CR-01
    // fix).
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }

    // (1b) Same-order dedup (K-IN-01/02): a re-emitted OrderConfirmed for the same order under a
    // fresh eventId passes the ledger check above but must not attempt a second ticket — absorb it
    // as a logged no-op instead of hitting the unique orderId constraint and landing on the DLT.
    // The unique constraint remains as a concurrent-insert backstop.
    if (kitchenTicketRepository.existsByOrderId(event.orderId())) {
      log.info(
          "Absorbing duplicate OrderConfirmed for orderId={} eventId={} — ticket already exists",
          event.orderId(),
          event.eventId());
      return;
    }

    // (2) Build the ticket and ALL its items in a single pass — never append items later.
    // Null-guard the manifest (IN-01): a malformed OrderConfirmedEvent with null lines must not
    // NPE — an NPE here would previously commit the ledger row while dropping the ticket forever.
    KitchenTicketEntity ticket = new KitchenTicketEntity();
    ticket.setOrderId(event.orderId());
    ticket.setCreatedAt(Instant.now());
    if (event.lines() != null) {
      for (OrderConfirmedLine line : event.lines()) {
        ticket.getItems().add(toItem(ticket, line));
      }
    }

    kitchenTicketRepository.save(ticket);

    // (3) Record the idempotency-ledger row LAST, in THIS transaction, so it commits atomically
    // with the ticket. A concurrent-duplicate unique violation rolls back the WHOLE transaction
    // (Kafka redelivers; the pre-check + same-order dedup then absorb the replay) instead of
    // pre-committing a "processed" marker in a separate REQUIRES_NEW transaction ahead of the
    // ticket save, which could drop the ticket forever on a later failure (CR-01 / I-WR-01 fix).
    recordProcessed(event.eventId());
  }

  private void recordProcessed(UUID eventId) {
    KitchenProcessedEventEntity ledger = new KitchenProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(CONSUMER_NAME);
    ledger.setProcessedAt(Instant.now());
    processedEventRepository.save(ledger);
  }

  private KitchenTicketItemEntity toItem(KitchenTicketEntity ticket, OrderConfirmedLine line) {
    KitchenTicketItemEntity item = new KitchenTicketItemEntity();
    item.setTicket(ticket);
    item.setOrderLineId(line.lineId());
    item.setDishId(line.dishId());
    item.setDishName(line.dishName());
    item.setQuantity(line.quantity());
    // Null-guard the topping list (IN-01): a malformed line with null selectedToppings must not
    // NPE.
    if (line.selectedToppings() != null) {
      for (OrderConfirmedTopping topping : line.selectedToppings()) {
        item.getSelectedToppings().add(toToppingSnapshot(topping));
      }
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
