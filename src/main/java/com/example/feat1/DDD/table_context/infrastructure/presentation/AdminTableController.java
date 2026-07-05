package com.example.feat1.DDD.table_context.infrastructure.presentation;

import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaResponse;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/tables")
@RequiredArgsConstructor
public class AdminTableController {
  private final TableCatalogService tableCatalogService;

  @PostMapping("/areas")
  public ResponseEntity<DiningAreaResponse> createArea(@RequestBody DiningAreaRequest request) {
    return ResponseEntity.ok(tableCatalogService.createArea(request));
  }

  @GetMapping("/areas")
  public ResponseEntity<List<DiningAreaResponse>> listAreas() {
    return ResponseEntity.ok(tableCatalogService.listAreas());
  }

  @GetMapping("/areas/{id}")
  public ResponseEntity<DiningAreaResponse> getArea(@PathVariable UUID id) {
    return ResponseEntity.ok(tableCatalogService.getArea(id));
  }

  @PutMapping("/areas/{id}")
  public ResponseEntity<DiningAreaResponse> updateArea(
      @PathVariable UUID id, @RequestBody DiningAreaRequest request) {
    return ResponseEntity.ok(tableCatalogService.updateArea(id, request));
  }

  @DeleteMapping("/areas/{id}")
  public ResponseEntity<DiningAreaResponse> archiveArea(@PathVariable UUID id) {
    return ResponseEntity.ok(tableCatalogService.archiveArea(id));
  }

  @PostMapping
  public ResponseEntity<DiningTableResponse> createTable(@RequestBody DiningTableRequest request) {
    return ResponseEntity.ok(tableCatalogService.createTable(request));
  }

  @GetMapping
  public ResponseEntity<List<DiningTableResponse>> listTables() {
    return ResponseEntity.ok(tableCatalogService.listTables());
  }

  @GetMapping("/{id}")
  public ResponseEntity<DiningTableResponse> getTable(@PathVariable UUID id) {
    return ResponseEntity.ok(tableCatalogService.getTable(id));
  }

  @PutMapping("/{id}")
  public ResponseEntity<DiningTableResponse> updateTable(
      @PathVariable UUID id, @RequestBody DiningTableRequest request) {
    return ResponseEntity.ok(tableCatalogService.updateTable(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<DiningTableResponse> archiveTable(@PathVariable UUID id) {
    return ResponseEntity.ok(tableCatalogService.archiveTable(id));
  }
}
