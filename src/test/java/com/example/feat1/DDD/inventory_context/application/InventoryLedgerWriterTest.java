package com.example.feat1.DDD.inventory_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.inventory_context.infrastructure.entity.InventoryProcessedEventEntity;
import com.example.feat1.DDD.inventory_context.infrastructure.repository.InventoryProcessedEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class InventoryLedgerWriterTest {

  private InventoryProcessedEventRepository processedEventRepository;
  private InventoryLedgerWriter ledgerWriter;

  @BeforeEach
  void setUp() {
    processedEventRepository = mock(InventoryProcessedEventRepository.class);
    ledgerWriter = new InventoryLedgerWriter(processedEventRepository);
  }

  @Test
  void tryInsertReturnsTrueWhenLedgerRowSaved() {
    UUID eventId = UUID.randomUUID();
    when(processedEventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

    boolean inserted = ledgerWriter.tryInsert(eventId, "inventory-settlement");

    assertThat(inserted).isTrue();
  }

  @Test
  void tryInsertReturnsFalseWithoutThrowingOnConcurrentDuplicate() {
    UUID eventId = UUID.randomUUID();
    doThrow(new DataIntegrityViolationException("duplicate (event_id, consumer_name)"))
        .when(processedEventRepository)
        .saveAndFlush(any(InventoryProcessedEventEntity.class));

    // WR-01: the isolated REQUIRES_NEW insert must swallow the duplicate and NOT propagate, so the
    // caller's business transaction is never marked rollback-only.
    boolean[] inserted = new boolean[1];
    assertThatCode(() -> inserted[0] = ledgerWriter.tryInsert(eventId, "inventory-settlement"))
        .doesNotThrowAnyException();
    assertThat(inserted[0]).isFalse();
  }
}
