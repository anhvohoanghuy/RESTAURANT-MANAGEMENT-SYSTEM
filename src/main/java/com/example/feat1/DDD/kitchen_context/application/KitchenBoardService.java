package com.example.feat1.DDD.kitchen_context.application;

import com.example.feat1.DDD.kitchen_context.application.dto.KitchenBoardItemResponse;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketItemRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only kitchen queue view: every item not yet {@link KitchenItemStatus#COMPLETED}, across all
 * tickets, ordered by ticket creation time then status so staff see the oldest, least-advanced work
 * first.
 */
@Service
@RequiredArgsConstructor
public class KitchenBoardService {

  private final KitchenTicketItemRepository itemRepository;

  @Transactional(readOnly = true)
  public List<KitchenBoardItemResponse> board() {
    return itemRepository.findByStatusNot(KitchenItemStatus.COMPLETED).stream()
        .sorted(
            Comparator.<KitchenTicketItemEntity, java.time.Instant>comparing(
                    item -> item.getTicket().getCreatedAt())
                .thenComparing(KitchenTicketItemEntity::getStatus))
        .map(this::toBoardItem)
        .toList();
  }

  private KitchenBoardItemResponse toBoardItem(KitchenTicketItemEntity item) {
    return new KitchenBoardItemResponse(
        item.getId(),
        item.getTicket().getOrderId(),
        item.getOrderLineId(),
        item.getDishName(),
        item.getQuantity(),
        item.getStatus());
  }
}
