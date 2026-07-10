package com.example.feat1.DDD.kitchen_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenProcessedEventEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenProcessedEventRepository;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

/**
 * Covers every behaviour bullet of plan 18-06 Task 2 (D-7): the guarded, idempotent void of
 * still-QUEUED kitchen items for cancelled order lines, mirroring {@code
 * KitchenTicketCreationServiceTest}'s mock-repository shape.
 */
class KitchenTicketInvalidationServiceTest {

  private static final String CONSUMER_NAME = "kitchen-order-cancelled";

  private KitchenProcessedEventRepository processedEventRepository;
  private KitchenTicketItemRepository itemRepository;
  private KitchenTicketInvalidationService service;

  private final UUID eventId = UUID.randomUUID();
  private final UUID orderId = UUID.randomUUID();
  private final UUID lineId1 = UUID.randomUUID();
  private final UUID lineId2 = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(KitchenProcessedEventRepository.class);
    itemRepository = mock(KitchenTicketItemRepository.class);
    service = new KitchenTicketInvalidationService(processedEventRepository, itemRepository);
  }

  @Test
  void queuedItemForCancelledLineIsVoidedToCancelled() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    KitchenTicketItemEntity item = itemWithStatus(lineId1, KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1))
        .thenReturn(Optional.of(item));

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.CANCELLED);
    verify(processedEventRepository, times(1)).save(any());
  }

  @Test
  void itemAlreadyPreparingOrBeyondIsNeverModified() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    KitchenTicketItemEntity preparing = itemWithStatus(lineId1, KitchenItemStatus.PREPARING);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1))
        .thenReturn(Optional.of(preparing));

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    assertThat(preparing.getStatus()).isEqualTo(KitchenItemStatus.PREPARING);
  }

  @Test
  void everyForwardStatusFromPreparingOnwardIsNeverModified() {
    when(processedEventRepository.existsByEventIdAndConsumerName(any(), any())).thenReturn(false);

    for (KitchenItemStatus status :
        List.of(
            KitchenItemStatus.PREPARING,
            KitchenItemStatus.READY,
            KitchenItemStatus.SERVED,
            KitchenItemStatus.COMPLETED)) {
      UUID lineId = UUID.randomUUID();
      KitchenTicketItemEntity item = itemWithStatus(lineId, status);
      when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId))
          .thenReturn(Optional.of(item));

      service.onOrderCancelled(cancelledEvent(List.of(lineId)));

      assertThat(item.getStatus()).isEqualTo(status);
    }
  }

  @Test
  void absentItemIsSkippedWithoutError() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1)).thenReturn(Optional.empty());

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    verify(processedEventRepository, times(1)).save(any());
  }

  @Test
  void replayOfSameEventIsANoOp() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(true);

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    verify(itemRepository, never()).lockByOrderIdAndOrderLineId(any(), any());
    verify(processedEventRepository, never()).save(any());
  }

  @Test
  void replayDoesNotResurrectOrReVoidAnAlreadyAdvancedItem() {
    // First delivery: not yet processed, item is QUEUED -> voided.
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false)
        .thenReturn(true);
    KitchenTicketItemEntity item = itemWithStatus(lineId1, KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1))
        .thenReturn(Optional.of(item));

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));
    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.CANCELLED);

    // Replay: staff has since (hypothetically) tried to advance it — but the ledger guard means
    // this handler never re-touches the item at all on redelivery.
    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    verify(itemRepository, times(1)).lockByOrderIdAndOrderLineId(orderId, lineId1);
    verify(processedEventRepository, times(1)).save(any());
  }

  @Test
  void multipleCancelledLinesAreEachLockedAndGuardedIndependently() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    KitchenTicketItemEntity queued = itemWithStatus(lineId1, KitchenItemStatus.QUEUED);
    KitchenTicketItemEntity preparing = itemWithStatus(lineId2, KitchenItemStatus.PREPARING);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1))
        .thenReturn(Optional.of(queued));
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId2))
        .thenReturn(Optional.of(preparing));

    service.onOrderCancelled(cancelledEvent(List.of(lineId1, lineId2)));

    assertThat(queued.getStatus()).isEqualTo(KitchenItemStatus.CANCELLED);
    assertThat(preparing.getStatus()).isEqualTo(KitchenItemStatus.PREPARING);
    verify(itemRepository, times(1)).lockByOrderIdAndOrderLineId(orderId, lineId1);
    verify(itemRepository, times(1)).lockByOrderIdAndOrderLineId(orderId, lineId2);
  }

  @Test
  void ledgerRowIsSavedLastAfterAllLineProcessing() {
    when(processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME))
        .thenReturn(false);
    KitchenTicketItemEntity item = itemWithStatus(lineId1, KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndOrderLineId(orderId, lineId1))
        .thenReturn(Optional.of(item));

    service.onOrderCancelled(cancelledEvent(List.of(lineId1)));

    InOrder inOrder = Mockito.inOrder(itemRepository, processedEventRepository);
    inOrder.verify(itemRepository).lockByOrderIdAndOrderLineId(orderId, lineId1);
    inOrder.verify(processedEventRepository).save(any());

    ArgumentCaptor<KitchenProcessedEventEntity> ledgerCaptor =
        ArgumentCaptor.forClass(KitchenProcessedEventEntity.class);
    verify(processedEventRepository).save(ledgerCaptor.capture());
    assertThat(ledgerCaptor.getValue().getEventId()).isEqualTo(eventId);
    assertThat(ledgerCaptor.getValue().getConsumerName()).isEqualTo(CONSUMER_NAME);
  }

  private OrderCancelledEvent cancelledEvent(List<UUID> cancelledLineIds) {
    return new OrderCancelledEvent(
        eventId,
        OrderCancelledEvent.TYPE,
        Instant.now(),
        orderId,
        false,
        cancelledLineIds,
        cancelledLineIds.size());
  }

  private KitchenTicketItemEntity itemWithStatus(UUID orderLineId, KitchenItemStatus status) {
    KitchenTicketEntity ticket = new KitchenTicketEntity();
    ticket.setId(UUID.randomUUID());
    ticket.setOrderId(orderId);
    ticket.setCreatedAt(Instant.now());

    KitchenTicketItemEntity item = new KitchenTicketItemEntity();
    item.setId(UUID.randomUUID());
    item.setTicket(ticket);
    item.setOrderLineId(orderLineId);
    item.setDishId(UUID.randomUUID());
    item.setDishName("Pho Bo");
    item.setQuantity(1);
    item.setStatus(status);

    ticket.getItems().add(item);
    return item;
  }
}
