package com.example.feat1.DDD.kitchen_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenProcessedEventRepository;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketRepository;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedLine;
import com.example.feat1.DDD.order_context.application.event.OrderConfirmedEvent.OrderConfirmedTopping;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KitchenTicketCreationServiceTest {

  private KitchenProcessedEventRepository processedEventRepository;
  private KitchenTicketRepository kitchenTicketRepository;
  private KitchenTicketCreationService service;

  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID lineId1 = UUID.randomUUID();
  private final UUID lineId2 = UUID.randomUUID();
  private final UUID dishId1 = UUID.randomUUID();
  private final UUID dishId2 = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(KitchenProcessedEventRepository.class);
    kitchenTicketRepository = mock(KitchenTicketRepository.class);
    service = new KitchenTicketCreationService(processedEventRepository, kitchenTicketRepository);
  }

  @Test
  void firstDeliveryCreatesOneTicketWithAllManifestItems() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(kitchenTicketRepository.existsByOrderId(any())).thenReturn(false);

    service.onOrderConfirmed(confirmedEvent());

    ArgumentCaptor<KitchenTicketEntity> captor = ArgumentCaptor.forClass(KitchenTicketEntity.class);
    verify(kitchenTicketRepository, times(1)).save(captor.capture());

    KitchenTicketEntity ticket = captor.getValue();
    assertThat(ticket.getOrderId()).isEqualTo(orderId);
    assertThat(ticket.getCreatedAt()).isNotNull();
    assertThat(ticket.getItems()).hasSize(2);

    KitchenTicketItemEntity item1 = ticket.getItems().get(0);
    assertThat(item1.getTicket()).isSameAs(ticket);
    assertThat(item1.getOrderLineId()).isEqualTo(lineId1);
    assertThat(item1.getDishId()).isEqualTo(dishId1);
    assertThat(item1.getDishName()).isEqualTo("Pho Bo");
    assertThat(item1.getQuantity()).isEqualTo(2);
    assertThat(item1.getStatus()).isEqualTo(KitchenItemStatus.QUEUED);
    assertThat(item1.getSelectedToppings()).hasSize(1);
    assertThat(item1.getSelectedToppings().get(0).getToppingGroupName()).isEqualTo("Size");

    KitchenTicketItemEntity item2 = ticket.getItems().get(1);
    assertThat(item2.getOrderLineId()).isEqualTo(lineId2);
    assertThat(item2.getDishId()).isEqualTo(dishId2);
    assertThat(item2.getQuantity()).isEqualTo(1);
    assertThat(item2.getSelectedToppings()).isEmpty();
  }

  @Test
  void redeliveryOfSameEventCreatesNoDuplicateTicket() {
    when(processedEventRepository.existsByEventIdAndConsumerName(
            eventId, "kitchen-order-confirmed"))
        .thenReturn(false)
        .thenReturn(true);

    service.onOrderConfirmed(confirmedEvent());
    service.onOrderConfirmed(confirmedEvent());

    verify(kitchenTicketRepository, times(1)).save(any());
  }

  @Test
  void concurrentDuplicateLedgerInsertPropagatesToRollBackTheWholeTransaction() {
    // CR-01 fix: the ledger row is inserted LAST in the same transaction as the ticket. A
    // concurrent-duplicate unique violation must propagate (not be swallowed) so the WHOLE
    // transaction — ticket + ledger row — rolls back and Kafka redelivers, instead of
    // pre-committing a "processed" marker ahead of an unguaranteed ticket save.
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(kitchenTicketRepository.existsByOrderId(any())).thenReturn(false);
    when(processedEventRepository.save(any()))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("dup"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.onOrderConfirmed(confirmedEvent()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void sameOrderDuplicateUnderNewEventIdIsAbsorbedWithoutSavingOrThrowing() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(kitchenTicketRepository.existsByOrderId(orderId)).thenReturn(true);

    service.onOrderConfirmed(confirmedEvent());

    verify(kitchenTicketRepository, never()).save(any());
  }

  @Test
  void newOrderIdWithNoExistingTicketBuildsAndSavesExactlyOnce() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(kitchenTicketRepository.existsByOrderId(orderId)).thenReturn(false);

    service.onOrderConfirmed(confirmedEvent());

    verify(kitchenTicketRepository, times(1)).save(any());
  }

  @Test
  void malformedEventWithNullLinesAndNullToppingsDoesNotThrowAndStillSavesTicket() {
    // IN-01: a malformed OrderConfirmedEvent (null lines, or a line with null selectedToppings)
    // must not NPE — an NPE here previously happened AFTER the ledger row was committed, dropping
    // the ticket forever with no recovery.
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
    when(kitchenTicketRepository.existsByOrderId(any())).thenReturn(false);

    OrderConfirmedLine nullToppingLine =
        new OrderConfirmedLine(lineId1, dishId1, "Pho Bo", 2, null);
    OrderConfirmedEvent nullToppings =
        new OrderConfirmedEvent(
            UUID.randomUUID(),
            OrderConfirmedEvent.TYPE,
            Instant.now(),
            orderId,
            List.of(nullToppingLine));
    service.onOrderConfirmed(nullToppings);

    OrderConfirmedEvent nullLines =
        new OrderConfirmedEvent(
            UUID.randomUUID(), OrderConfirmedEvent.TYPE, Instant.now(), UUID.randomUUID(), null);
    service.onOrderConfirmed(nullLines);

    verify(kitchenTicketRepository, times(2)).save(any());
  }

  private OrderConfirmedEvent confirmedEvent() {
    OrderConfirmedTopping topping =
        new OrderConfirmedTopping(UUID.randomUUID(), "Size", UUID.randomUUID(), "Large");
    OrderConfirmedLine line1 =
        new OrderConfirmedLine(lineId1, dishId1, "Pho Bo", 2, List.of(topping));
    OrderConfirmedLine line2 = new OrderConfirmedLine(lineId2, dishId2, "Goi Cuon", 1, List.of());
    return new OrderConfirmedEvent(
        eventId, OrderConfirmedEvent.TYPE, Instant.now(), orderId, List.of(line1, line2));
  }
}
