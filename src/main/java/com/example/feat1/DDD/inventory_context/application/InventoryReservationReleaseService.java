package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.domain.port.OrderLineLookupPort;
import com.example.feat1.DDD.inventory_context.domain.service.RecipeRequirementResolver;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryLineReleaseRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryLineSettlementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockBalanceRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryStockMovementRepository;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.StockReservationRepository;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases held reservations for cancelled order lines — the structural inverse of {@link
 * InventoryReservationSettlementService}. Stub pending implementation (TDD RED phase).
 */
@Service
@RequiredArgsConstructor
public class InventoryReservationReleaseService {

  public static final String CONSUMER_NAME = "inventory-release";

  private final InventoryProcessedEventRepository processedEventRepository;
  private final InventoryLineReleaseRepository lineReleaseRepository;
  private final InventoryLineSettlementRepository lineSettlementRepository;
  private final StockReservationRepository reservationRepository;
  private final InventoryStockBalanceRepository balanceRepository;
  private final InventoryStockMovementRepository movementRepository;
  private final OrderLineLookupPort orderLineLookupPort;
  private final RecipeRequirementResolver recipeRequirementResolver;

  @Transactional
  public void onOrderCancelled(OrderCancelledEvent event) {
    // TDD RED: not yet implemented.
  }
}
