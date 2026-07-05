package com.example.feat1.DDD.table_context.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicTablesResponse;
import com.example.feat1.DDD.table_context.domain.model.DiningArea;
import com.example.feat1.DDD.table_context.domain.model.DiningTable;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.repository.DiningAreaDomainRepository;
import com.example.feat1.DDD.table_context.domain.repository.DiningTableDomainRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableCatalogServiceTest {
  private DiningAreaDomainRepository areaRepository;
  private DiningTableDomainRepository tableRepository;
  private TableCatalogService service;

  @BeforeEach
  void setUp() {
    areaRepository = mock(DiningAreaDomainRepository.class);
    tableRepository = mock(DiningTableDomainRepository.class);
    service = new TableCatalogService(areaRepository, tableRepository);
  }

  @Test
  void publicTablesReturnActiveAreaTableTree() {
    UUID areaId = UUID.randomUUID();
    DiningArea area = new DiningArea(areaId, "Main Hall", 1, TableStatus.ACTIVE);
    DiningTable table =
        new DiningTable(UUID.randomUUID(), areaId, "A01", "Table A01", 4, 1, TableStatus.ACTIVE);
    when(areaRepository.findActiveOrdered()).thenReturn(List.of(area));
    when(tableRepository.findActiveByAreaIds(List.of(areaId))).thenReturn(List.of(table));

    PublicTablesResponse response = service.getPublicTables();

    assertThat(response.areas()).hasSize(1);
    assertThat(response.areas().get(0).name()).isEqualTo("Main Hall");
    assertThat(response.areas().get(0).tables()).extracting("code").containsExactly("A01");
  }

  @Test
  void validateOrderableTableReturnsSnapshot() {
    UUID areaId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    DiningArea area = new DiningArea(areaId, "VIP Room", 2, TableStatus.ACTIVE);
    DiningTable table =
        new DiningTable(tableId, areaId, "VIP-01", "VIP Table 01", 6, 1, TableStatus.ACTIVE);
    when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));

    var snapshot = service.validateOrderableTable(tableId);

    assertThat(snapshot.tableId()).isEqualTo(tableId);
    assertThat(snapshot.code()).isEqualTo("VIP-01");
    assertThat(snapshot.areaName()).isEqualTo("VIP Room");
  }

  @Test
  void validateOrderableTableRejectsInactiveTableWithStableCode() {
    UUID areaId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    when(tableRepository.findById(tableId))
        .thenReturn(
            Optional.of(
                new DiningTable(tableId, areaId, "A02", "Table A02", 2, 2, TableStatus.INACTIVE)));

    assertThatThrownBy(() -> service.validateOrderableTable(tableId))
        .isInstanceOf(TableDomainException.class)
        .extracting("code")
        .isEqualTo(TableDomainException.TABLE_NOT_ORDERABLE);
  }

  @Test
  void validateOrderableTableRejectsInactiveAreaWithStableCode() {
    UUID areaId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    when(tableRepository.findById(tableId))
        .thenReturn(
            Optional.of(
                new DiningTable(tableId, areaId, "A03", "Table A03", 2, 3, TableStatus.ACTIVE)));
    when(areaRepository.findById(areaId))
        .thenReturn(Optional.of(new DiningArea(areaId, "Closed Area", 1, TableStatus.ARCHIVED)));

    assertThatThrownBy(() -> service.validateOrderableTable(tableId))
        .isInstanceOf(TableDomainException.class)
        .extracting("code")
        .isEqualTo(TableDomainException.TABLE_AREA_NOT_ORDERABLE);
  }

  @Test
  void tableCapacityMustBePositiveWhenProvided() {
    assertThatThrownBy(
            () ->
                new DiningTable(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "B01",
                    "Table B01",
                    0,
                    1,
                    TableStatus.ACTIVE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("capacity");
  }

  @Test
  void createTableRejectsDuplicateStableCode() {
    UUID areaId = UUID.randomUUID();
    DiningArea area = new DiningArea(areaId, "Main Hall", 1, TableStatus.ACTIVE);
    DiningTable existing =
        new DiningTable(UUID.randomUUID(), areaId, "A01", "Table A01", 4, 1, TableStatus.ACTIVE);
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(tableRepository.findByCode("A01")).thenReturn(Optional.of(existing));

    assertThatThrownBy(
            () ->
                service.createTable(
                    new DiningTableRequest(areaId, "A01", "Another A01", 2, 2, TableStatus.ACTIVE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("code already exists");
  }

  @Test
  void archiveTableMarksTableArchived() {
    UUID areaId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    DiningArea area = new DiningArea(areaId, "Main Hall", 1, TableStatus.ACTIVE);
    DiningTable table =
        new DiningTable(tableId, areaId, "A01", "Table A01", 4, 1, TableStatus.ACTIVE);
    when(tableRepository.findById(tableId)).thenReturn(Optional.of(table));
    when(areaRepository.findById(areaId)).thenReturn(Optional.of(area));
    when(tableRepository.save(any(DiningTable.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.archiveTable(tableId);

    assertThat(response.status()).isEqualTo(TableStatus.ARCHIVED);
  }
}
