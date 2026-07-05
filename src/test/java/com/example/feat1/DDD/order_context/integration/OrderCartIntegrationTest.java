package com.example.feat1.DDD.order_context.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.example.feat1.DDD.table_context.application.TableCatalogService;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningAreaRequest;
import com.example.feat1.DDD.table_context.application.dto.TableDtos.DiningTableRequest;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
class OrderCartIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MenuCatalogService menuCatalogService;
  @Autowired private TableCatalogService tableCatalogService;

  @MockitoBean private RefreshTokenCache refreshTokenCache;
  @MockitoBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockitoBean private EmailNotificationPort emailNotificationPort;
  @MockitoBean private AuthRateLimitService authRateLimitService;
  @MockitoBean private LoginLockoutService loginLockoutService;

  @Test
  void authenticatedUserCanManageCartWithStoredSnapshotsAndMergedLines() throws Exception {
    String accessToken = registerAndGetAccessToken("cart");
    UUID tableId = createTable("CART");
    MenuFixture menu = createMenu("cart");

    MvcResult firstAdd =
        mockMvc
            .perform(
                post("/cart/items")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tableId":"%s","dishId":"%s","toppingOptionIds":["%s","%s"],"quantity":2}
                        """
                            .formatted(tableId, menu.dishId(), menu.toppingB(), menu.toppingA())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.table.code").value("CART-01"))
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.lines[0].quantity").value(2))
            .andExpect(jsonPath("$.lines[0].unitPrice").value(80000))
            .andReturn();

    String lineId = JsonPath.read(firstAdd.getResponse().getContentAsString(), "$.lines[0].lineId");

    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","dishId":"%s","toppingOptionIds":["%s","%s"],"quantity":3}
                    """
                        .formatted(tableId, menu.dishId(), menu.toppingA(), menu.toppingB())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(1))
        .andExpect(jsonPath("$.lines[0].quantity").value(5))
        .andExpect(jsonPath("$.total").value(400000));

    mockMvc
        .perform(
            patch("/cart/items/{lineId}", lineId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].quantity").value(1))
        .andExpect(jsonPath("$.total").value(80000));

    mockMvc
        .perform(
            delete("/cart/items/{lineId}", lineId).header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(0))
        .andExpect(jsonPath("$.table").doesNotExist());

    mockMvc
        .perform(get("/cart").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines.length()").value(0))
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void otherUserCannotDeleteCartLineOwnedByFirstUser() throws Exception {
    String firstToken = registerAndGetAccessToken("owner");
    String secondToken = registerAndGetAccessToken("intruder");
    UUID tableId = createTable("OWN");
    MenuFixture menu = createMenu("owner");

    MvcResult result =
        mockMvc
            .perform(
                post("/cart/items")
                    .header("Authorization", "Bearer " + firstToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tableId":"%s","dishId":"%s","toppingOptionIds":[],"quantity":1}
                        """
                            .formatted(tableId, menu.dishId())))
            .andExpect(status().isOk())
            .andReturn();

    String lineId = JsonPath.read(result.getResponse().getContentAsString(), "$.lines[0].lineId");

    mockMvc
        .perform(
            delete("/cart/items/{lineId}", lineId).header("Authorization", "Bearer " + secondToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ORDER_CART_LINE_NOT_FOUND"));
  }

  @Test
  void anonymousCartRequestsAreRejectedAndInvalidQuantityHasStableCode() throws Exception {
    String accessToken = registerAndGetAccessToken("invalid");
    UUID tableId = createTable("INV");
    MenuFixture menu = createMenu("invalid");

    mockMvc.perform(get("/cart")).andExpect(status().isUnauthorized());

    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","dishId":"%s","toppingOptionIds":[],"quantity":0}
                    """
                        .formatted(tableId, menu.dishId())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ORDER_QUANTITY_INVALID"));
  }

  @Test
  void oneActiveCartIsStoredPerUser() throws Exception {
    String accessToken = registerAndGetAccessToken("single");

    MvcResult first =
        mockMvc
            .perform(get("/cart").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    MvcResult second =
        mockMvc
            .perform(get("/cart").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();

    String firstCartId = JsonPath.read(first.getResponse().getContentAsString(), "$.cartId");
    String secondCartId = JsonPath.read(second.getResponse().getContentAsString(), "$.cartId");
    assertThat(secondCartId).isEqualTo(firstCartId);
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
    var toppingB =
        menuCatalogService.createToppingOption(
            new ToppingOptionRequest(
                group.id(),
                prefix + " Topping B " + suffix,
                BigDecimal.valueOf(10000),
                MenuStatus.ACTIVE,
                2));
    return new MenuFixture(dish.id(), toppingA.id(), toppingB.id());
  }

  private record MenuFixture(UUID dishId, UUID toppingA, UUID toppingB) {}
}
