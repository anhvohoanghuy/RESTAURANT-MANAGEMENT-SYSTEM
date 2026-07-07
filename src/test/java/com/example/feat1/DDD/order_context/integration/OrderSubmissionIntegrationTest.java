package com.example.feat1.DDD.order_context.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.auth.application.AuthRateLimitService;
import com.example.feat1.DDD.auth.application.LoginLockoutService;
import com.example.feat1.DDD.auth.application.auth_service.google.GoogleIdTokenVerifier;
import com.example.feat1.DDD.auth.application.auth_service.refresh.RefreshTokenCache;
import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.order_context.application.event.OrderCreatedEvent;
import com.example.feat1.DDD.order_context.domain.port.OrderEventPublisher;
import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.TableOperationService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.OpenTableSessionRequest;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.port.TableOperationEventPublisher;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
    properties = {
      "jwt.access-expiration=900000",
      "jwt.refresh-expiration=604800000",
      "jwt.secret=test-secret-test-secret-test-secret-32",
      "table.seed.enabled=true"
    })
class OrderSubmissionIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MenuCatalogService menuCatalogService;
  @Autowired private TableCatalogService tableCatalogService;
  @Autowired private TableOperationService tableOperationService;

  @MockitoBean private RefreshTokenCache refreshTokenCache;
  @MockitoBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockitoBean private EmailNotificationPort emailNotificationPort;
  @MockitoBean private AuthRateLimitService authRateLimitService;
  @MockitoBean private LoginLockoutService loginLockoutService;
  @MockitoBean private OrderEventPublisher orderEventPublisher;
  @MockitoBean private TableOperationEventPublisher tableOperationEventPublisher;

  @Test
  void submitCartCreatesOrderPublishesEventAndClearsCart() throws Exception {
    String accessToken = registerAndGetAccessToken("submit");
    UUID tableId = createTable("SUB");
    UUID tableSessionId =
        tableOperationService
            .openSession(
                tableId, new OpenTableSessionRequest(2, "order submit", null), UUID.randomUUID())
            .sessionId();
    MenuFixture menu = createMenu("submit");

    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","tableSessionId":"%s","dishId":"%s","toppingOptionIds":["%s"],"quantity":2}
                    """
                        .formatted(tableId, tableSessionId, menu.dishId(), menu.toppingA())))
        .andExpect(status().isOk());

    MvcResult submitResult =
        mockMvc
            .perform(post("/orders").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.tableId").value(tableId.toString()))
            .andExpect(jsonPath("$.table.tableSessionId").value(tableSessionId.toString()))
            .andExpect(jsonPath("$.table.code").value("SUB-01"))
            .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.lines[0].dishId").value(menu.dishId().toString()))
            .andExpect(
                jsonPath("$.lines[0].selectedToppings[0].toppingOptionId")
                    .value(menu.toppingA().toString()))
            .andExpect(jsonPath("$.lines[0].quantity").value(2))
            .andExpect(jsonPath("$.total").value(140000))
            .andReturn();

    String orderId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.orderId");

    ArgumentCaptor<OrderCreatedEvent> eventCaptor =
        ArgumentCaptor.forClass(OrderCreatedEvent.class);
    verify(orderEventPublisher).publishOrderCreated(eventCaptor.capture());
    assertThat(eventCaptor.getValue().orderId()).isEqualTo(UUID.fromString(orderId));
    assertThat(eventCaptor.getValue().table().tableId()).isEqualTo(tableId);
    assertThat(eventCaptor.getValue().table().tableSessionId()).isEqualTo(tableSessionId);
    assertThat(eventCaptor.getValue().lines()).hasSize(1);

    mockMvc
        .perform(get("/orders/{orderId}", orderId).header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderId").value(orderId))
        .andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"))
        .andExpect(jsonPath("$.lines.length()").value(1));

    mockMvc
        .perform(get("/cart").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(0))
        .andExpect(jsonPath("$.table").doesNotExist());
  }

  @Test
  void submitEmptyCartFailsWithStableCodeAndDoesNotPublish() throws Exception {
    String accessToken = registerAndGetAccessToken("empty-submit");

    mockMvc
        .perform(post("/orders").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORDER_CART_EMPTY"));

    verify(orderEventPublisher, never()).publishOrderCreated(any());
  }

  @Test
  void otherUserCannotReadSubmittedOrder() throws Exception {
    String ownerToken = registerAndGetAccessToken("order-owner");
    String intruderToken = registerAndGetAccessToken("order-intruder");
    UUID tableId = createTable("OWNO");
    MenuFixture menu = createMenu("owner-order");

    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","dishId":"%s","toppingOptionIds":[],"quantity":1}
                    """
                        .formatted(tableId, menu.dishId())))
        .andExpect(status().isOk());

    MvcResult submitResult =
        mockMvc
            .perform(post("/orders").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andReturn();
    String orderId = JsonPath.read(submitResult.getResponse().getContentAsString(), "$.orderId");

    mockMvc
        .perform(
            get("/orders/{orderId}", orderId).header("Authorization", "Bearer " + intruderToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
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

  private record MenuFixture(UUID dishId, UUID toppingA) {}
}
