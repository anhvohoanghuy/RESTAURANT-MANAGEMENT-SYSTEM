package com.example.feat1.DDD.table_context.application;

import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.repository.DiningAreaDomainRepository;
import com.example.feat1.DDD.table_context.domain.repository.DiningTableDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "table.seed.enabled", havingValue = "true")
public class TableSeedInitializer implements CommandLineRunner {
  private final TableCatalogService tableCatalogService;
  private final DiningAreaDomainRepository areaRepository;
  private final DiningTableDomainRepository tableRepository;

  @Override
  public void run(String... args) {
    seedAreaWithTables("Main Hall", 1, "A01", "Table A01", 4, "A02", "Table A02", 2);
    seedAreaWithTables("VIP Room", 2, "VIP-01", "VIP Table 01", 6, "VIP-02", "VIP Table 02", 8);
  }

  private void seedAreaWithTables(
      String areaName,
      int areaSortOrder,
      String firstCode,
      String firstName,
      int firstCapacity,
      String secondCode,
      String secondName,
      int secondCapacity) {
    var area =
        areaRepository
            .findByName(areaName)
            .map(existing -> tableCatalogService.getArea(existing.getId()))
            .orElseGet(
                () ->
                    tableCatalogService.createArea(
                        new DiningAreaRequest(areaName, areaSortOrder, TableStatus.ACTIVE)));

    seedTable(area.id(), firstCode, firstName, firstCapacity, 1);
    seedTable(area.id(), secondCode, secondName, secondCapacity, 2);
  }

  private void seedTable(
      java.util.UUID areaId, String code, String name, int capacity, int sortOrder) {
    if (tableRepository.findByCode(code).isEmpty()) {
      tableCatalogService.createTable(
          new DiningTableRequest(areaId, code, name, capacity, sortOrder, TableStatus.ACTIVE));
    }
  }
}
