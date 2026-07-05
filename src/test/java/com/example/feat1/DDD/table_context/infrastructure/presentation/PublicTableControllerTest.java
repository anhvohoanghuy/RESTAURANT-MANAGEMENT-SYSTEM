package com.example.feat1.DDD.table_context.infrastructure.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicDiningArea;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicDiningTable;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.PublicTablesResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicTableControllerTest {
  private final TableCatalogService service = Mockito.mock(TableCatalogService.class);
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new PublicTableController(service)).build();

  @Test
  void publicTablesReturnAreaTableTree() throws Exception {
    when(service.getPublicTables())
        .thenReturn(
            new PublicTablesResponse(
                List.of(
                    new PublicDiningArea(
                        UUID.randomUUID(),
                        "Main Hall",
                        1,
                        List.of(
                            new PublicDiningTable(UUID.randomUUID(), "A01", "Table A01", 4, 1))))));

    mockMvc
        .perform(get("/tables/public").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.areas[0].name").value("Main Hall"))
        .andExpect(jsonPath("$.areas[0].tables[0].code").value("A01"))
        .andExpect(jsonPath("$.areas[0].tables[0].capacity").value(4));
  }
}
