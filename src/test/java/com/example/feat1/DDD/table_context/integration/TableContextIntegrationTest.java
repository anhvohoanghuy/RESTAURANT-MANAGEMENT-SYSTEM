package com.example.feat1.DDD.table_context.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.infrastructure.repository.DiningAreaRepository;
import com.example.feat1.DDD.table_context.infrastructure.repository.DiningTableRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "table.seed.enabled=true")
class TableContextIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TableCatalogService tableCatalogService;
  @Autowired private DiningAreaRepository areaRepository;
  @Autowired private DiningTableRepository tableRepository;

  @Test
  void seedCreatesSampleAreasAndTables() {
    assertThat(areaRepository.findByName("Main Hall")).isPresent();
    assertThat(tableRepository.findByCode("A01")).isPresent();
  }

  @Test
  void adminCrudAndPublicListingExposeOnlyActiveTables() throws Exception {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    MvcResult areaResult =
        mockMvc
            .perform(
                post("/admin/tables/areas")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Terrace %s","sortOrder":10,"status":"ACTIVE"}
                        """
                            .formatted(suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Terrace " + suffix))
            .andReturn();
    String areaId = JsonPath.read(areaResult.getResponse().getContentAsString(), "$.id");

    MvcResult tableResult =
        mockMvc
            .perform(
                post("/admin/tables")
                    .with(user("admin").roles("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"areaId":"%s","code":"T-%s","name":"Terrace Table","capacity":4,"sortOrder":1,"status":"ACTIVE"}
                        """
                            .formatted(areaId, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("T-" + suffix))
            .andReturn();
    String tableId = JsonPath.read(tableResult.getResponse().getContentAsString(), "$.id");

    mockMvc
        .perform(get("/tables/public").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.areas[*].name", hasItem("Terrace " + suffix)));

    mockMvc
        .perform(delete("/admin/tables/{id}", tableId).with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(TableStatus.ARCHIVED.name()));

    assertThat(
            assertThatThrownByCode(
                () -> tableCatalogService.validateOrderableTable(UUID.fromString(tableId))))
        .isEqualTo(TableDomainException.TABLE_NOT_ORDERABLE);
  }

  @Test
  void nonAdminCannotUseAdminTableEndpointsButPublicListingIsAnonymous() throws Exception {
    mockMvc.perform(get("/tables/public")).andExpect(status().isOk());

    mockMvc
        .perform(
            post("/admin/tables/areas")
                .with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Blocked\",\"sortOrder\":1,\"status\":\"ACTIVE\"}"))
        .andExpect(status().isForbidden());
  }

  private String assertThatThrownByCode(Runnable action) {
    try {
      action.run();
    } catch (TableDomainException exception) {
      return exception.getCode();
    }
    throw new AssertionError("Expected TableDomainException");
  }
}
