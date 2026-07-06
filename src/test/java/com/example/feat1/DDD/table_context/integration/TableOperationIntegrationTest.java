package com.example.feat1.DDD.table_context.integration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.port.TableOperationEventPublisher;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "table.seed.enabled=true")
class TableOperationIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private TableCatalogService tableCatalogService;

  @MockitoBean private TableOperationEventPublisher tableOperationEventPublisher;

  @Test
  void staffCanOpenCloseAndChangeOccupancyWhilePublicAvailabilityExcludesBusyTables()
      throws Exception {
    UUID tableId = createTable("OPS");
    Instant from = Instant.now().plusSeconds(3600);
    Instant to = from.plusSeconds(3600);

    mockMvc
        .perform(
            post("/admin/tables/{tableId}/sessions", tableId)
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2,\"note\":\"walk in\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tableId").value(tableId.toString()))
        .andExpect(jsonPath("$.status").value("OPEN"));

    mockMvc
        .perform(
            post("/admin/tables/{tableId}/sessions", tableId)
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"partySize\":2}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TABLE_SESSION_ALREADY_OPEN"));

    mockMvc
        .perform(get("/admin/tables/occupancy").with(staff()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.tableId == '%s')].state".formatted(tableId), hasItem("OCCUPIED")));

    mockMvc
        .perform(
            get("/tables/public/availability")
                .param("from", from.toString())
                .param("to", to.toString())
                .param("partySize", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tables[*].tableId", not(hasItem(tableId.toString()))));

    MvcResult occupancyResult =
        mockMvc
            .perform(get("/admin/tables/occupancy").with(staff()))
            .andExpect(status().isOk())
            .andReturn();
    List<String> sessionIds =
        JsonPath.read(
            occupancyResult.getResponse().getContentAsString(),
            "$[?(@.tableId == '%s')].activeSession.sessionId".formatted(tableId));
    String sessionId = sessionIds.get(0);

    mockMvc
        .perform(
            post("/admin/table-sessions/{sessionId}/close", sessionId)
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextState\":\"CLEANING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));

    mockMvc
        .perform(
            patch("/admin/tables/{tableId}/occupancy", tableId)
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"state\":\"OUT_OF_SERVICE\",\"reason\":\"maintenance\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("OUT_OF_SERVICE"));

    mockMvc
        .perform(
            get("/tables/public/availability")
                .param("from", from.toString())
                .param("to", to.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tables[*].tableId", not(hasItem(tableId.toString()))));

    verify(tableOperationEventPublisher, atLeast(3))
        .publish(org.mockito.ArgumentMatchers.any(TableOperationEvent.class));
  }

  @Test
  void staffCanReserveConfirmAndSeatWithoutOverlappingReservations() throws Exception {
    UUID tableId = createTable("RSV");
    Instant start = Instant.now().plusSeconds(7200);
    Instant end = start.plusSeconds(3600);

    MvcResult reservationResult =
        mockMvc
            .perform(
                post("/admin/tables/reservations")
                    .with(staff())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tableId":"%s","customerName":"Linh","customerPhone":"0900000000","partySize":3,"startTime":"%s","endTime":"%s"}
                        """
                            .formatted(tableId, start, end)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
    String reservationId =
        JsonPath.read(reservationResult.getResponse().getContentAsString(), "$.reservationId");

    mockMvc
        .perform(
            post("/admin/tables/reservations")
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","customerName":"Minh","customerPhone":"0911111111","partySize":2,"startTime":"%s","endTime":"%s"}
                    """
                        .formatted(tableId, start.plusSeconds(600), end.plusSeconds(600))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TABLE_RESERVATION_OVERLAP"));

    mockMvc
        .perform(
            patch("/admin/tables/reservations/{reservationId}/status", reservationId)
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CONFIRMED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    mockMvc
        .perform(
            post("/admin/tables/reservations/{reservationId}/seat", reservationId).with(staff()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.reservationId").value(reservationId));
  }

  private UUID createTable(String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    var area =
        tableCatalogService.createArea(
            new DiningAreaRequest(prefix + " Area " + suffix, 1, TableStatus.ACTIVE));
    var table =
        tableCatalogService.createTable(
            new DiningTableRequest(
                area.id(), prefix + "-" + suffix, prefix + " Table", 4, 1, TableStatus.ACTIVE));
    return table.id();
  }

  private RequestPostProcessor staff() {
    CustomUserDetails principal =
        CustomUserDetails.builder()
            .id(UUID.randomUUID())
            .email("staff@example.com")
            .password("secret")
            .roles(Set.of("STAFF"))
            .build();
    return authentication(
        new UsernamePasswordAuthenticationToken(
            principal, principal.getPassword(), principal.getAuthorities()));
  }
}
