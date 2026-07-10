package com.example.feat1.DDD.order_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent;
import com.example.feat1.DDD.kitchen_context.application.event.KitchenTicketStatusChangedEvent.ItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderProcessedEventRepository;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the order-side consumer half of D-04: {@link KitchenStatusProjectionService}
 * derives the order's fulfillment status from kitchen's full per-item snapshot, applies it strictly
 * forward via {@code FULFILLMENT_RANK}, and never regresses or touches a REJECTED order (Pitfall 3,
 * T-17-17). Idempotency is guarded by the shared {@code order_processed_events} ledger (T-17-19).
 */
class KitchenStatusProjectionServiceTest {

  private OrderProcessedEventRepository processedEventRepository;
  private OrderRepository orderRepository;
  private KitchenStatusProjectionService service;

  private final UUID orderId = UUID.randomUUID();
  private final UUID ticketId = UUID.randomUUID();
  private final UUID lineId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(OrderProcessedEventRepository.class);
    orderRepository = mock(OrderRepository.class);
    service = new KitchenStatusProjectionService(processedEventRepository, orderRepository);
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);
  }

  @Test
  void anyItemPreparingSetsConfirmedOrderToPreparing() {
    OrderEntity order = orderWithStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
  }

  @Test
  void allItemsReadySetsOrderToReady() {
    OrderEntity order = orderWithStatus(OrderStatus.PREPARING);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(
        event(
            new ItemStatus(lineId, KitchenItemStatus.READY),
            new ItemStatus(UUID.randomUUID(), KitchenItemStatus.READY)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
  }

  @Test
  void notAllItemsReadyKeepsOrderAtPreparing() {
    OrderEntity order = orderWithStatus(OrderStatus.PREPARING);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(
        event(
            new ItemStatus(lineId, KitchenItemStatus.READY),
            new ItemStatus(UUID.randomUUID(), KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
  }

  @Test
  void allItemsServedSetsOrderToServed() {
    OrderEntity order = orderWithStatus(OrderStatus.READY);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.SERVED)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.SERVED);
  }

  @Test
  void allItemsCompletedSetsOrderToCompleted() {
    OrderEntity order = orderWithStatus(OrderStatus.SERVED);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.COMPLETED)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
  }

  @Test
  void replayedPreparingAfterReadyDoesNotRegressOrder() {
    OrderEntity order = orderWithStatus(OrderStatus.READY);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    // Out-of-order / replayed delivery: an earlier PREPARING snapshot arrives after READY was
    // already applied to the order. The rank guard must keep the order at READY.
    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
  }

  @Test
  void unknownCurrentRankFailsClosedAndDoesNotAdvance() {
    // Order is still pre-CONFIRMED (PENDING_CONFIRMATION has no FULFILLMENT_RANK entry). A
    // fulfillment snapshot must never fail-open past this unknown rank (K-WR-03).
    OrderEntity order = orderWithStatus(OrderStatus.PENDING_CONFIRMATION);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
  }

  @Test
  void confirmedOrderStillAdvancesToPreparing() {
    // Forward transition from a known rank (CONFIRMED) must still work after the fail-closed
    // guard is added (K-WR-03).
    OrderEntity order = orderWithStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PREPARING);
  }

  @Test
  void readyOrderDoesNotRegressToPreparing() {
    OrderEntity order = orderWithStatus(OrderStatus.READY);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
  }

  @Test
  void rejectedOrderIsNeverModified() {
    OrderEntity order = orderWithStatus(OrderStatus.REJECTED);
    when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.COMPLETED)));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
  }

  @Test
  void duplicateEventIdIsNoOpAndNeverLoadsOrder() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(true);

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));

    verify(orderRepository, never()).findById(any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void orderNotFoundIsNoOpAndDoesNotThrow() {
    when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

    service.onTicketStatusChanged(event(new ItemStatus(lineId, KitchenItemStatus.PREPARING)));
  }

  @Test
  @SuppressWarnings("unchecked")
  void itemRankPinsTheIntendedOrderingIndependentOfEnumDeclarationOrder() throws Exception {
    // WR-05: deriveTargetStatus must depend on an explicit ITEM_RANK map, not
    // KitchenItemStatus.ordinal(), so a future reorder of the enum's declaration cannot silently
    // change status derivation. This test reads ITEM_RANK directly via reflection and asserts the
    // INTENDED semantic ranking (QUEUED < PREPARING < READY < SERVED < COMPLETED) -- it does not
    // consult KitchenItemStatus.ordinal() anywhere, so it keeps pinning the correct ranking even
    // if the enum's declaration order is later changed.
    java.lang.reflect.Field field =
        KitchenStatusProjectionService.class.getDeclaredField("ITEM_RANK");
    field.setAccessible(true);
    Map<KitchenItemStatus, Integer> itemRank = (Map<KitchenItemStatus, Integer>) field.get(null);

    List<KitchenItemStatus> intendedOrder =
        List.of(
            KitchenItemStatus.QUEUED,
            KitchenItemStatus.PREPARING,
            KitchenItemStatus.READY,
            KitchenItemStatus.SERVED,
            KitchenItemStatus.COMPLETED);

    assertThat(itemRank.keySet()).containsExactlyInAnyOrderElementsOf(intendedOrder);
    for (int i = 0; i < intendedOrder.size() - 1; i++) {
      int lower = itemRank.get(intendedOrder.get(i));
      int higher = itemRank.get(intendedOrder.get(i + 1));
      assertThat(higher)
          .as(
              "rank(%s) should be strictly less than rank(%s)",
              intendedOrder.get(i), intendedOrder.get(i + 1))
          .isGreaterThan(lower);
    }
  }

  private OrderEntity orderWithStatus(OrderStatus status) {
    OrderEntity order = new OrderEntity();
    order.setId(orderId);
    order.setStatus(status);
    return order;
  }

  private KitchenTicketStatusChangedEvent event(ItemStatus... items) {
    return new KitchenTicketStatusChangedEvent(
        UUID.randomUUID(),
        KitchenTicketStatusChangedEvent.TYPE,
        Instant.now(),
        orderId,
        ticketId,
        List.of(items));
  }
}
