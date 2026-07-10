package com.example.feat1.DDD.inventory_context.application;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryLedgerWriter {
  private final InventoryProcessedEventRepository processedEventRepository;
  private final TransactionTemplate requiresNew;

  public InventoryLedgerWriter(
      InventoryProcessedEventRepository processedEventRepository,
      PlatformTransactionManager transactionManager) {
    this.processedEventRepository = processedEventRepository;
    this.requiresNew = new TransactionTemplate(transactionManager);
    this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public boolean tryInsert(UUID eventId, String consumerName) {
    try {
      requiresNew.executeWithoutResult(
          status -> processedEventRepository.saveAndFlush(ledger(eventId, consumerName)));
      return true;
    } catch (DataIntegrityViolationException duplicate) {
      return false;
    }
  }

  private InventoryProcessedEventEntity ledger(UUID eventId, String consumerName) {
    InventoryProcessedEventEntity ledger = new InventoryProcessedEventEntity();
    ledger.setEventId(eventId);
    ledger.setConsumerName(consumerName);
    ledger.setProcessedAt(Instant.now());
    return ledger;
  }
}
