package com.example.feat1.DDD.order_context.application;

import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.CancelOrderLinesRequest;
import com.example.feat1.DDD.order_context.application.dto.OrderCancellationDtos.OrderCancellationResponse;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.order_context.domain.model.KitchenItemStatusView;
import com.example.feat1.DDD.order_context.domain.model.OrderDomainException;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.domain.port.KitchenItemStatusPort;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderLineEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.application.OutboxWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side of order cancellation (CANCEL-01/02/04): whole-order cancel and partial
 * item-cancel, both under a pessimistic order-row lock, both gated by the
 * SUBMITTED/PENDING_CONFIRMATION/CONFIRMED cancel window, both race-safely excluding any line whose
 * kitchen item has already reached PREPARING via a synchronous {@link KitchenItemStatusPort} read
 * taken inside the same locked transaction, and both emitting exactly one {@link
 * OrderCancelledEvent} through the transactional outbox on success.
 *
 * <p>A {@code null} {@code userId} denotes the staff/ADMIN path (no ownership check, REST-layer
 * authorization already scopes {@code /admin/orders/**}); a non-null {@code userId} denotes the
 * customer path and is resolved via the existing IDOR-safe {@code findByIdAndUserId} lookup before
 * the row is locked.
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

  private static final Set<OrderStatus> CANCELLABLE_STATUSES =
      EnumSet.of(OrderStatus.SUBMITTED, OrderStatus.PENDING_CONFIRMATION, OrderStatus.CONFIRMED);

  private final OrderRepository orderRepository;
  private final KitchenItemStatusPort kitchenItemStatusPort;
  private final OutboxWriter outboxWriter;

  @Value("${order.events.order-cancelled-topic:orders.cancelled}")
  private String orderCancelledTopic;

  @Transactional
  public OrderCancellationResponse cancelOrder(UUID userId, UUID orderId) {
    OrderEntity order = resolveAndLock(userId, orderId);
    return applyCancellation(order, true, List.of());
  }

  @Transactional
  public OrderCancellationResponse cancelOrderLines(
      UUID userId, UUID orderId, CancelOrderLinesRequest request) {
    OrderEntity order = resolveAndLock(userId, orderId);
    List<UUID> requestedLineIds =
        request == null || request.lineIds() == null ? List.of() : request.lineIds();
    return applyCancellation(order, false, requestedLineIds);
  }

  private OrderEntity resolveAndLock(UUID userId, UUID orderId) {
    if (userId != null) {
      // IDOR-safe ownership pre-check BEFORE any lock is acquired (T-18-02-01): a non-owner gets
      // the same 404 as a genuinely missing order, and never causes a row lock on someone else's
      // order.
      orderRepository
          .findByIdAndUserId(orderId, userId)
          .orElseThrow(OrderDomainException::orderNotFound);
    }
    return orderRepository.lockById(orderId).orElseThrow(OrderDomainException::orderNotFound);
  }

  private OrderCancellationResponse applyCancellation(
      OrderEntity order, boolean wholeOrder, List<UUID> requestedLineIds) {
    if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
      throw OrderDomainException.cancelWindowClosed();
    }

    // Synchronous cross-context read taken INSIDE the locked transaction, immediately before
    // deciding which lines are cancellable (T-18-02-03): closes the cancel-vs-kitchen-advance
    // race that an eventually-consistent projection could not.
    Map<UUID, KitchenItemStatusView> kitchenStatuses =
        kitchenItemStatusPort.findStatuses(order.getId());

    List<OrderLineEntity> candidateLines = candidateLines(order, wholeOrder, requestedLineIds);

    Instant now = Instant.now();
    List<UUID> cancelledLineIds = new ArrayList<>();
    for (OrderLineEntity line : candidateLines) {
      if (line.getCancelledAt() != null) {
        continue;
      }
      KitchenItemStatusView status = kitchenStatuses.get(line.getId());
      boolean beforePreparing = status == null || status.isBeforePreparing();
      if (!beforePreparing) {
        continue;
      }
      line.setCancelledAt(now);
      cancelledLineIds.add(line.getId());
    }

    if (!wholeOrder && cancelledLineIds.isEmpty()) {
      throw OrderDomainException.noCancellableLines();
    }

    if (wholeOrder) {
      order.setStatus(OrderStatus.CANCELLED);
    }

    order.setTotal(recomputeTotal(order));

    OrderCancelledEvent event =
        new OrderCancelledEvent(
            UUID.randomUUID(),
            OrderCancelledEvent.TYPE,
            now,
            order.getId(),
            wholeOrder,
            cancelledLineIds,
            order.getLines().size());
    outboxWriter.save(
        "ORDER",
        order.getId(),
        OrderCancelledEvent.TYPE,
        orderCancelledTopic,
        order.getId().toString(),
        event);

    return toResponse(order, cancelledLineIds);
  }

  private List<OrderLineEntity> candidateLines(
      OrderEntity order, boolean wholeOrder, List<UUID> requestedLineIds) {
    if (wholeOrder) {
      return order.getLines();
    }
    Set<UUID> requested = new HashSet<>(requestedLineIds);
    return order.getLines().stream().filter(line -> requested.contains(line.getId())).toList();
  }

  private BigDecimal recomputeTotal(OrderEntity order) {
    return order.getLines().stream()
        .filter(line -> line.getCancelledAt() == null)
        .map(OrderLineEntity::getLineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private OrderCancellationResponse toResponse(OrderEntity order, List<UUID> cancelledLineIds) {
    return new OrderCancellationResponse(
        order.getId(), order.getStatus(), order.getTotal(), cancelledLineIds);
  }
}
