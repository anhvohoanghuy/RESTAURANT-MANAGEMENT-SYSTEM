package com.example.feat1.DDD.table_context.application;

import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaResponse;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableResponse;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicDiningArea;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicDiningTable;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicTablesResponse;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.TableSnapshot;
import com.example.feat1.DDD.table_context.domain.model.DiningArea;
import com.example.feat1.DDD.table_context.domain.model.DiningTable;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.repository.DiningAreaDomainRepository;
import com.example.feat1.DDD.table_context.domain.repository.DiningTableDomainRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TableCatalogService {
  private final DiningAreaDomainRepository areaRepository;
  private final DiningTableDomainRepository tableRepository;

  @Transactional
  public DiningAreaResponse createArea(DiningAreaRequest request) {
    DiningArea area =
        new DiningArea(null, request.name(), defaultInt(request.sortOrder()), request.status());
    return toAreaResponse(areaRepository.save(area));
  }

  @Transactional
  public DiningAreaResponse updateArea(UUID id, DiningAreaRequest request) {
    findArea(id);
    DiningArea area =
        new DiningArea(id, request.name(), defaultInt(request.sortOrder()), request.status());
    return toAreaResponse(areaRepository.save(area));
  }

  @Transactional(readOnly = true)
  public List<DiningAreaResponse> listAreas() {
    return areaRepository.findAllOrdered().stream().map(this::toAreaResponse).toList();
  }

  @Transactional(readOnly = true)
  public DiningAreaResponse getArea(UUID id) {
    return toAreaResponse(findArea(id));
  }

  @Transactional
  public DiningAreaResponse archiveArea(UUID id) {
    DiningArea area = findArea(id);
    return toAreaResponse(
        areaRepository.save(
            new DiningArea(
                area.getId(), area.getName(), area.getSortOrder(), TableStatus.ARCHIVED)));
  }

  @Transactional
  public DiningTableResponse createTable(DiningTableRequest request) {
    DiningArea area = findArea(request.areaId());
    DiningTable table =
        new DiningTable(
            null,
            area.getId(),
            request.code(),
            request.name(),
            request.capacity(),
            defaultInt(request.sortOrder()),
            request.status());
    ensureCodeAvailable(table.getCode(), null);
    return toTableResponse(tableRepository.save(table), area);
  }

  @Transactional
  public DiningTableResponse updateTable(UUID id, DiningTableRequest request) {
    findTable(id);
    DiningArea area = findArea(request.areaId());
    DiningTable table =
        new DiningTable(
            id,
            area.getId(),
            request.code(),
            request.name(),
            request.capacity(),
            defaultInt(request.sortOrder()),
            request.status());
    ensureCodeAvailable(table.getCode(), id);
    return toTableResponse(tableRepository.save(table), area);
  }

  @Transactional(readOnly = true)
  public List<DiningTableResponse> listTables() {
    return tableRepository.findAllOrdered().stream()
        .map(table -> toTableResponse(table, findArea(table.getAreaId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public DiningTableResponse getTable(UUID id) {
    DiningTable table = findTable(id);
    return toTableResponse(table, findArea(table.getAreaId()));
  }

  @Transactional
  public DiningTableResponse archiveTable(UUID id) {
    DiningTable table = findTable(id);
    DiningArea area = findArea(table.getAreaId());
    return toTableResponse(
        tableRepository.save(
            new DiningTable(
                table.getId(),
                table.getAreaId(),
                table.getCode(),
                table.getName(),
                table.getCapacity(),
                table.getSortOrder(),
                TableStatus.ARCHIVED)),
        area);
  }

  @Transactional(readOnly = true)
  public PublicTablesResponse getPublicTables() {
    List<DiningArea> areas = areaRepository.findActiveOrdered();
    List<UUID> areaIds = areas.stream().map(DiningArea::getId).toList();
    List<DiningTable> tables = tableRepository.findActiveByAreaIds(areaIds);
    Map<UUID, List<DiningTable>> tablesByArea =
        tables.stream().collect(Collectors.groupingBy(DiningTable::getAreaId));

    return new PublicTablesResponse(
        areas.stream()
            .map(
                area ->
                    new PublicDiningArea(
                        area.getId(),
                        area.getName(),
                        area.getSortOrder(),
                        tablesByArea.getOrDefault(area.getId(), List.of()).stream()
                            .sorted(bySortThenCode())
                            .map(
                                table ->
                                    new PublicDiningTable(
                                        table.getId(),
                                        table.getCode(),
                                        table.getName(),
                                        table.getCapacity(),
                                        table.getSortOrder()))
                            .toList()))
            .toList());
  }

  @Transactional(readOnly = true)
  public TableSnapshot validateOrderableTable(UUID tableId) {
    if (tableId == null) {
      throw TableDomainException.tableNotOrderable();
    }

    DiningTable table =
        tableRepository.findById(tableId).orElseThrow(TableDomainException::tableNotOrderable);
    if (table.getStatus() != TableStatus.ACTIVE) {
      throw TableDomainException.tableNotOrderable();
    }

    DiningArea area =
        areaRepository
            .findById(table.getAreaId())
            .orElseThrow(TableDomainException::tableAreaNotOrderable);
    if (area.getStatus() != TableStatus.ACTIVE) {
      throw TableDomainException.tableAreaNotOrderable();
    }

    return new TableSnapshot(
        table.getId(), table.getCode(), table.getName(), area.getId(), area.getName());
  }

  private DiningArea findArea(UUID id) {
    if (id == null) {
      throw new EntityNotFoundException("Dining area not found");
    }
    return areaRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Dining area not found"));
  }

  private DiningTable findTable(UUID id) {
    if (id == null) {
      throw new EntityNotFoundException("Dining table not found");
    }
    return tableRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Dining table not found"));
  }

  private void ensureCodeAvailable(String code, UUID currentTableId) {
    tableRepository
        .findByCode(code)
        .filter(existing -> !existing.getId().equals(currentTableId))
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException("Dining table code already exists");
            });
  }

  private DiningAreaResponse toAreaResponse(DiningArea area) {
    return new DiningAreaResponse(
        area.getId(), area.getName(), area.getSortOrder(), area.getStatus());
  }

  private DiningTableResponse toTableResponse(DiningTable table, DiningArea area) {
    return new DiningTableResponse(
        table.getId(),
        table.getAreaId(),
        area.getName(),
        table.getCode(),
        table.getName(),
        table.getCapacity(),
        table.getSortOrder(),
        table.getStatus());
  }

  private int defaultInt(Integer value) {
    return value == null ? 0 : value;
  }

  private Comparator<DiningTable> bySortThenCode() {
    return Comparator.comparingInt(DiningTable::getSortOrder)
        .thenComparing(DiningTable::getCode, Comparator.nullsLast(String::compareTo));
  }
}
