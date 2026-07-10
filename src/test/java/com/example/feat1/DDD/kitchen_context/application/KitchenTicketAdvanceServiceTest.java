package com.example.feat1.DDD.kitchen_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.kitchen_context.application.dto.AdvanceItemStatusRequest;
import com.example.feat1.DDD.kitchen_context.application.dto.KitchenItemResponse;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenDomainException;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenSettleTriggerPublisher;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenTicketStatusChangedPublisher;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KitchenTicketAdvanceServiceTest {

  private KitchenTicketItemRepository itemRepository;
  private KitchenSettleTriggerPublisher settleTriggerPublisher;
  private KitchenTicketStatusChangedPublisher statusChangedPublisher;
  private KitchenTicketAdvanceService service;

  private final UUID orderId = UUID.randomUUID();
  private final UUID itemId = UUID.randomUUID();
  private final UUID orderLineId = UUID.randomUUID();
  private final UUID actorId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    itemRepository = mock(KitchenTicketItemRepository.class);
    settleTriggerPublisher = mock(KitchenSettleTriggerPublisher.class);
    statusChangedPublisher = mock(KitchenTicketStatusChangedPublisher.class);
    when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    service =
        new KitchenTicketAdvanceService(
            itemRepository, settleTriggerPublisher, statusChangedPublisher);
  }

  @Test
  void queuedToPreparingPersistsStatusAndPublishesSettleTriggerAndStatusChanged() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    KitchenItemResponse response =
        service.advance(
            orderId, itemId, new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING), actorId);

    assertThat(response.status()).isEqualTo(KitchenItemStatus.PREPARING);
    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.PREPARING);
    verify(itemRepository, times(1)).save(item);

    ArgumentCaptor<SettleTriggerEvent> captor = ArgumentCaptor.forClass(SettleTriggerEvent.class);
    verify(settleTriggerPublisher, times(1)).publishSettleTrigger(captor.capture());
    SettleTriggerEvent published = captor.getValue();
    assertThat(published.orderId()).isEqualTo(orderId);
    assertThat(published.orderLineId()).isEqualTo(orderLineId);
    assertThat(published.totalLines()).isEqualTo(item.getTicket().getItems().size());
    assertThat(published.eventType()).isEqualTo(SettleTriggerEvent.TYPE);

    verify(statusChangedPublisher, times(1)).publishTicketStatusChanged(any());

    assertThat(item.getAdvancedBy()).isEqualTo(actorId);
    assertThat(item.getAdvancedAt()).isNotNull();
  }

  @Test
  void preparingToReadyPublishesStatusChangedButNotSettleTrigger() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.PREPARING);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    KitchenItemResponse response =
        service.advance(
            orderId, itemId, new AdvanceItemStatusRequest(KitchenItemStatus.READY), actorId);

    assertThat(response.status()).isEqualTo(KitchenItemStatus.READY);
    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, times(1)).publishTicketStatusChanged(any());
  }

  @Test
  void readyToServedPublishesStatusChangedButNotSettleTrigger() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.READY);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    service.advance(
        orderId, itemId, new AdvanceItemStatusRequest(KitchenItemStatus.SERVED), actorId);

    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, times(1)).publishTicketStatusChanged(any());
  }

  @Test
  void servedToCompletedPublishesStatusChangedButNotSettleTrigger() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.SERVED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    service.advance(
        orderId, itemId, new AdvanceItemStatusRequest(KitchenItemStatus.COMPLETED), actorId);

    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, times(1)).publishTicketStatusChanged(any());
  }

  @Test
  void skippingAStepIsRejectedWithNoMutationAndNoPublish() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.READY),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_TRANSITION_INVALID);

    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.QUEUED);
    verify(itemRepository, never()).save(any());
    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, never()).publishTicketStatusChanged(any());
    assertThat(item.getAdvancedBy()).isNull();
    assertThat(item.getAdvancedAt()).isNull();
  }

  @Test
  void revertingAStepIsRejectedWithNoMutationAndNoPublish() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.READY);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_TRANSITION_INVALID);

    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.READY);
    verify(itemRepository, never()).save(any());
    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, never()).publishTicketStatusChanged(any());
  }

  @Test
  void advancingFromTerminalCompletedIsRejected() {
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.COMPLETED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.COMPLETED),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_TRANSITION_INVALID);

    verify(itemRepository, never()).save(any());
    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, never()).publishTicketStatusChanged(any());
  }

  @Test
  void advancingFromVoidedCancelledIsRejected() {
    // 18-06 / D-7 regression: a kitchen item voided by KitchenTicketInvalidationService must be a
    // true terminal state — staff can never advance it (which would otherwise fire a rogue
    // SettleTrigger deducting stock already released back to Inventory).
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.CANCELLED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_TRANSITION_INVALID);

    assertThat(item.getStatus()).isEqualTo(KitchenItemStatus.CANCELLED);
    verify(itemRepository, never()).save(any());
    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, never()).publishTicketStatusChanged(any());
  }

  @Test
  void itemNotFoundForGivenOrderThrowsItemNotFound() {
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_ITEM_NOT_FOUND);

    verify(settleTriggerPublisher, never()).publishSettleTrigger(any());
    verify(statusChangedPublisher, never()).publishTicketStatusChanged(any());
  }

  @Test
  void concurrentDoubleAdvanceOnSameItemCannotDoublePublishSettleTrigger() {
    // First transaction locks the row, reads QUEUED, advances to PREPARING, publishes once.
    KitchenTicketItemEntity item = itemWithStatus(KitchenItemStatus.QUEUED);
    when(itemRepository.lockByOrderIdAndItemId(orderId, itemId)).thenReturn(Optional.of(item));

    service.advance(
        orderId, itemId, new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING), actorId);

    // Second transaction re-reads the row after the first committed: lock now returns the
    // already-mutated PREPARING item, so a second QUEUED->PREPARING request is illegal.
    assertThatThrownBy(
            () ->
                service.advance(
                    orderId,
                    itemId,
                    new AdvanceItemStatusRequest(KitchenItemStatus.PREPARING),
                    actorId))
        .isInstanceOf(KitchenDomainException.class)
        .hasFieldOrPropertyWithValue("code", KitchenDomainException.KITCHEN_TRANSITION_INVALID);

    verify(settleTriggerPublisher, times(1)).publishSettleTrigger(any());
  }

  private KitchenTicketItemEntity itemWithStatus(KitchenItemStatus status) {
    KitchenTicketEntity ticket = new KitchenTicketEntity();
    ticket.setId(UUID.randomUUID());
    ticket.setOrderId(orderId);
    ticket.setCreatedAt(Instant.now());

    KitchenTicketItemEntity item = new KitchenTicketItemEntity();
    item.setId(itemId);
    item.setTicket(ticket);
    item.setOrderLineId(orderLineId);
    item.setDishId(UUID.randomUUID());
    item.setDishName("Pho Bo");
    item.setQuantity(2);
    item.setStatus(status);

    ticket.getItems().add(item);
    return item;
  }
}
