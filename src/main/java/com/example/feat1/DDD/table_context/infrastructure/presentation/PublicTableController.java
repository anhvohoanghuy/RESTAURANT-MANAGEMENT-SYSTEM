package com.example.feat1.DDD.table_context.infrastructure.presentation;

import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicTablesResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tables")
@RequiredArgsConstructor
public class PublicTableController {
  private final TableCatalogService tableCatalogService;

  @GetMapping("/public")
  public ResponseEntity<PublicTablesResponse> getPublicTables() {
    return ResponseEntity.ok(tableCatalogService.getPublicTables());
  }
}
