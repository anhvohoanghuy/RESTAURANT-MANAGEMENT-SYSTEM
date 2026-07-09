package com.example.feat1.DDD.kitchen_context.application;

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
  private final KitchenLedgerWriter ledgerWriter;

  @Transactional
  public void onOrderConfirmed(OrderConfirmedEvent event) {
    // (1) Idempotency: fast pre-check, then delegate the authoritative insert to a REQUIRES_NEW
    // writer (I-WR-01) so a concurrent-duplicate violation rolls back only the inner ledger
    // transaction instead of poisoning this ticket-creation transaction.
    if (processedEventRepository.existsByEventIdAndConsumerName(event.eventId(), CONSUMER_NAME)) {
      return;
    }
    if (!ledgerWriter.tryInsert(event.eventId(), CONSUMER_NAME)) {
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
