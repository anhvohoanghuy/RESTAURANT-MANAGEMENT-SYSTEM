package com.example.feat1.DDD.kitchen_context.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.kitchen_context.domain.model.KitchenItemStatus;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenSettleTriggerPublisher;
import com.example.feat1.DDD.kitchen_context.domain.port.KitchenTicketStatusChangedPublisher;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.entity.KitchenTicketItemEntity;
import com.example.feat1.DDD.kitchen_context.infrastructure.repository.KitchenTicketRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * End-to-end coverage of D-05: the staff advance PATCH and the kitchen board GET, both living under
 * the already-secured {@code /admin/orders/**} route (verified untouched — see the "no
 * SecurityConfig modification" assertion implicit in reusing the existing RBAC without any new
 * {@code @PreAuthorize}/security config in this plan).
 */
@SpringBootTest
@AutoConfigureMockMvc
class KitchenIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private KitchenTicketRepository kitchenTicketRepository;

  // Publisher ports are mocked so no real Kafka broker call happens during the test (mirrors
  // OrderSubmissionIntegrationTest / TableOperationIntegrationTest's MockitoBean-the-port pattern).
  @MockitoBean private KitchenSettleTriggerPublisher kitchenSettleTriggerPublisher;
  @MockitoBean private KitchenTicketStatusChangedPublisher kitchenTicketStatusChangedPublisher;

  @Test
  void staffAdvancesQueuedItemToPreparingThenIllegalTransitionIsRejected() throws Exception {
    UUID orderId = UUID.randomUUID();
    KitchenTicketItemEntity item = seedTicketWithItem(orderId, KitchenItemStatus.QUEUED);

    mockMvc
        .perform(
            patch("/admin/orders/{orderId}/items/{itemId}/status", orderId, item.getId())
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"PREPARING\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PREPARING"))
        .andExpect(jsonPath("$.itemId").value(item.getId().toString()));

    // Illegal transition: PREPARING -> READY is legal, but skipping straight to SERVED is not.
    mockMvc
        .perform(
            patch("/admin/orders/{orderId}/items/{itemId}/status", orderId, item.getId())
                .with(staff())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"SERVED\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("KITCHEN_TRANSITION_INVALID"));
  }

  @Test
  void kitchenBoardListsActiveItemsExcludingCompleted() throws Exception {
    UUID activeOrderId = UUID.randomUUID();
    UUID completedOrderId = UUID.randomUUID();
    KitchenTicketItemEntity activeItem =
        seedTicketWithItem(activeOrderId, KitchenItemStatus.QUEUED);
    KitchenTicketItemEntity completedItem =
        seedTicketWithItem(completedOrderId, KitchenItemStatus.COMPLETED);

    mockMvc
        .perform(get("/admin/orders/kitchen-board").with(staff()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.itemId=='%s')]".formatted(activeItem.getId())).exists())
        .andExpect(
            jsonPath("$[?(@.itemId=='%s')]".formatted(completedItem.getId())).doesNotExist());
  }

  @Test
  void nonStaffAndAnonymousCallersAreDenied() throws Exception {
    UUID orderId = UUID.randomUUID();
    KitchenTicketItemEntity item = seedTicketWithItem(orderId, KitchenItemStatus.QUEUED);

    mockMvc
        .perform(get("/admin/orders/kitchen-board").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/admin/orders/kitchen-board")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            patch("/admin/orders/{orderId}/items/{itemId}/status", orderId, item.getId())
                .with(user("user").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"PREPARING\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            patch("/admin/orders/{orderId}/items/{itemId}/status", orderId, item.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetStatus\":\"PREPARING\"}"))
        .andExpect(status().isUnauthorized());
  }

  private KitchenTicketItemEntity seedTicketWithItem(UUID orderId, KitchenItemStatus status) {
    KitchenTicketEntity ticket = new KitchenTicketEntity();
    ticket.setOrderId(orderId);
    ticket.setCreatedAt(Instant.now());

    KitchenTicketItemEntity item = new KitchenTicketItemEntity();
    item.setTicket(ticket);
    item.setOrderLineId(UUID.randomUUID());
    item.setDishId(UUID.randomUUID());
    item.setDishName("Pho Bo");
    item.setQuantity(2);
    item.setStatus(status);
    ticket.getItems().add(item);

    kitchenTicketRepository.saveAndFlush(ticket);
    return ticket.getItems().get(0);
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
