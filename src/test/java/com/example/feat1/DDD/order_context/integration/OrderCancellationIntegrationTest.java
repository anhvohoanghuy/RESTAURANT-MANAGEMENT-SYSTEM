package com.example.feat1.DDD.order_context.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.auth.application.AuthRateLimitService;
import com.example.feat1.DDD.auth.application.LoginLockoutService;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.order_context.application.event.OrderCancelledEvent;
import com.example.feat1.DDD.order_context.domain.model.OrderStatus;
import com.example.feat1.DDD.order_context.infrastructure.entity.OrderEntity;
import com.example.feat1.DDD.order_context.infrastructure.repository.OrderRepository;
import com.example.feat1.DDD.shared.outbox.entity.OutboxEventEntity;
import com.example.feat1.DDD.shared.outbox.repository.OutboxEventRepository;
import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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

/**
 * End-to-end coverage of the cancellation authorization matrix (CANCEL-02/03/04): a customer
 * cancelling their own in-window order, IDOR-safe 404 for a non-owner, staff/ADMIN cancelling any
 * order via {@code /admin/orders/**}, a plain USER rejected from that same admin route by {@code
 * SecurityConfig}'s existing {@code hasAnyRole("ADMIN","STAFF")} matcher (untouched by this plan),
 * and the cancel-window guard once an order has moved past the customer-cancellable statuses.
 * Mirrors {@code OrderSubmissionIntegrationTest}'s MockMvc + H2 harness; the outbox relay is
 * disabled by the test profile ({@code outbox.relay.enabled=false}) so the {@code OrderCancelled}
 * event is asserted as a persisted outbox row rather than a live Kafka publish.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "jwt.access-expiration=900000",
      "jwt.refresh-expiration=604800000",
      "jwt.secret=test-secret-test-secret-test-secret-32",
      "table.seed.enabled=true"
    })
class OrderCancellationIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MenuCatalogService menuCatalogService;
  @Autowired private TableCatalogService tableCatalogService;
  @Autowired private OutboxEventRepository outboxEventRepository;
  @Autowired private OrderRepository orderRepository;

  @MockitoBean private RefreshTokenCache refreshTokenCache;
  @MockitoBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockitoBean private EmailNotificationPort emailNotificationPort;
  @MockitoBean private AuthRateLimitService authRateLimitService;
  @MockitoBean private LoginLockoutService loginLockoutService;

  @Test
  void customerCancelsOwnOrderAndLeavesCancelledOutboxRow() throws Exception {
    String accessToken = registerAndGetAccessToken("cancel-owner");
    UUID orderId = submitOrder(accessToken, "CXO");

    mockMvc
        .perform(
            post("/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    List<OutboxEventEntity> pendingRows =
        outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING");
    OutboxEventEntity outboxRow =
        pendingRows.stream()
            .filter(row -> row.getAggregateId().equals(orderId))
            .filter(row -> row.getEventType().equals(OrderCancelledEvent.TYPE))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("No PENDING OrderCancelled outbox row for " + orderId));
    assertThat(outboxRow.getAggregateType()).isEqualTo("ORDER");
  }

  @Test
  void customerCancellingAnotherUsersOrderIsIdorSafe404() throws Exception {
    String ownerToken = registerAndGetAccessToken("cancel-target");
    String intruderToken = registerAndGetAccessToken("cancel-intruder");
    UUID orderId = submitOrder(ownerToken, "CXI");

    mockMvc
        .perform(
            post("/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + intruderToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

    OrderEntity untouched = orderRepository.findById(orderId).orElseThrow();
    assertThat(untouched.getStatus()).isNotEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void staffCancelsAnyOrderViaAdminRoute() throws Exception {
    String customerToken = registerAndGetAccessToken("cancel-staffcase");
    UUID orderId = submitOrder(customerToken, "CXS");

    mockMvc
        .perform(post("/admin/orders/{orderId}/cancel", orderId).with(staff()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId.toString()))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void nonStaffUserIsRejectedFromAdminCancelRoute() throws Exception {
    String customerToken = registerAndGetAccessToken("cancel-nonstaff");
    UUID orderId = submitOrder(customerToken, "CXN");

    mockMvc
        .perform(
            post("/admin/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + customerToken))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(post("/admin/orders/{orderId}/cancel", orderId))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void cancellingOrderPastCancelWindowIsRejected() throws Exception {
    String accessToken = registerAndGetAccessToken("cancel-window");
    UUID orderId = submitOrder(accessToken, "CXW");

    OrderEntity order = orderRepository.findById(orderId).orElseThrow();
    order.setStatus(OrderStatus.PREPARING);
    orderRepository.saveAndFlush(order);

    mockMvc
        .perform(
            post("/orders/{orderId}/cancel", orderId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORDER_CANCEL_WINDOW_CLOSED"));
  }

  private UUID submitOrder(String accessToken, String tablePrefix) throws Exception {
    UUID tableId = createTable(tablePrefix);
    MenuFixture menu = createMenu(tablePrefix);

    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","dishId":"%s","toppingOptionIds":["%s"],"quantity":1}
                    """
                        .formatted(tableId, menu.dishId(), menu.toppingA())))
        .andExpect(status().isOk());

    MvcResult submitResult =
        mockMvc
            .perform(post("/orders").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    String orderId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.orderId");
    return UUID.fromString(orderId);
  }

  private String registerAndGetAccessToken(String prefix) throws Exception {
    String username = prefix + "-" + UUID.randomUUID();
    String email = username + "@example.com";
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","email":"%s","password":"secret123"}
                        """
                            .formatted(username, email)))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  private UUID createTable(String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    var area =
        tableCatalogService.createArea(
            new DiningAreaRequest(prefix + " Area " + suffix, 1, TableStatus.ACTIVE));
    var table =
        tableCatalogService.createTable(
            new DiningTableRequest(
                area.id(), prefix + "-01", prefix + " Table", 4, 1, TableStatus.ACTIVE));
    return table.id();
  }

  private MenuFixture createMenu(String prefix) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    var category =
        menuCatalogService.createCategory(
            new CategoryRequest(prefix + " Category " + suffix, null, 1, MenuStatus.ACTIVE));
    var dish =
        menuCatalogService.createDish(
            new DishRequest(
                category.id(),
                prefix + " Dish " + suffix,
                null,
                BigDecimal.valueOf(65000),
                MenuStatus.ACTIVE,
                1));
    var group =
        menuCatalogService.createToppingGroup(
            new ToppingGroupRequest(dish.id(), prefix + " Toppings " + suffix, 0, 2, 1));
    var toppingA =
        menuCatalogService.createToppingOption(
            new ToppingOptionRequest(
                group.id(),
                prefix + " Topping A " + suffix,
                BigDecimal.valueOf(5000),
                MenuStatus.ACTIVE,
                1));
    return new MenuFixture(dish.id(), toppingA.id());
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

  private record MenuFixture(UUID dishId, UUID toppingA) {}
}
