package com.example.feat1.DDD.table_context.infrastructure.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaResponse;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableResponse;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminTableControllerTest {
  private final TableCatalogService service = Mockito.mock(TableCatalogService.class);
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new AdminTableController(service)).build();

  @Test
  void adminCanCreateAndListAreas() throws Exception {
    UUID areaId = UUID.randomUUID();
    when(service.createArea(any(DiningAreaRequest.class)))
        .thenReturn(new DiningAreaResponse(areaId, "Main Hall", 1, TableStatus.ACTIVE));
    when(service.listAreas())
        .thenReturn(List.of(new DiningAreaResponse(areaId, "Main Hall", 1, TableStatus.ACTIVE)));

    mockMvc
        .perform(
            post("/admin/tables/areas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Main Hall\",\"sortOrder\":1,\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Main Hall"));

    mockMvc
        .perform(get("/admin/tables/areas").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").value("Main Hall"));
  }

  @Test
  void adminCanCreateAndArchiveTable() throws Exception {
    UUID areaId = UUID.randomUUID();
    UUID tableId = UUID.randomUUID();
    when(service.createTable(any(DiningTableRequest.class)))
        .thenReturn(
            new DiningTableResponse(
                tableId, areaId, "Main Hall", "A01", "Table A01", 4, 1, TableStatus.ACTIVE));
    when(service.archiveTable(tableId))
        .thenReturn(
            new DiningTableResponse(
                tableId, areaId, "Main Hall", "A01", "Table A01", 4, 1, TableStatus.ARCHIVED));

    mockMvc
        .perform(
            post("/admin/tables")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"areaId":"%s","code":"A01","name":"Table A01","capacity":4,"sortOrder":1,"status":"ACTIVE"}
                    """
                        .formatted(areaId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("A01"));

    mockMvc
        .perform(delete("/admin/tables/{id}", tableId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }
}
