package com.example.feat1.DDD.payment_context.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
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
import com.example.feat1.DDD.identity_context.application.dto.RoleEnum;
import com.example.feat1.DDD.identity_context.infastructure.entity.UserRoleEntity;
import com.example.feat1.DDD.identity_context.infastructure.repository.IRoleRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRepository;
import com.example.feat1.DDD.identity_context.infastructure.repository.IUserRoleRepository;
import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingGroupRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.ToppingOptionRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.payment_context.application.event.PaymentEvent;
import com.example.feat1.DDD.payment_context.domain.port.PaymentEventPublisher;
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
      "table.seed.enabled=true",
      "payment.qr.placeholder-base-url=http://pay.local/qr"
    })
class PaymentCheckoutIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MenuCatalogService menuCatalogService;
  @Autowired private TableCatalogService tableCatalogService;
  @Autowired private IUserRepository userRepository;
  @Autowired private IRoleRepository roleRepository;
  @Autowired private IUserRoleRepository userRoleRepository;

  @MockitoBean private RefreshTokenCache refreshTokenCache;
  @MockitoBean private GoogleIdTokenVerifier googleIdTokenVerifier;
  @MockitoBean private EmailNotificationPort emailNotificationPort;
  @MockitoBean private AuthRateLimitService authRateLimitService;
  @MockitoBean private LoginLockoutService loginLockoutService;
  @MockitoBean private PaymentEventPublisher paymentEventPublisher;

  @Test
  void staffRecordsPaymentsAndOrderSummaryReflectsStatus() throws Exception {
    RegisteredUser owner = register("payment-owner");
    String staffToken = registerStaffAndLogin("payment-staff");
    UUID orderId = submitOrder(owner.accessToken(), "PAY");

    mockMvc
        .perform(
            post("/admin/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + owner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":70000,"method":"CASH","idempotencyKey":"owner-forbidden"}
                    """))
        .andExpect(status().isForbidden());

    MvcResult firstPayment =
        mockMvc
            .perform(
                post("/admin/orders/{orderId}/payments", orderId)
                    .header("Authorization", "Bearer " + staffToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"amount":70000,"method":"CASH","idempotencyKey":"pay-partial","reference":"cashbox-1"}
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.amount").value(70000))
            .andExpect(jsonPath("$.method").value("CASH"))
            .andReturn();
    String paymentId =
        JsonPath.read(firstPayment.getResponse().getContentAsString(), "$.paymentId");

    mockMvc
        .perform(
            post("/admin/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":70000,"method":"CASH","idempotencyKey":"pay-partial"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paymentId").value(paymentId));

    mockMvc
        .perform(
            get("/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment.paymentStatus").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.payment.paidAmount").value(70000))
        .andExpect(jsonPath("$.payment.remainingAmount").value(70000));

    mockMvc
        .perform(
            post("/admin/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":70000,"method":"BANK_TRANSFER","idempotencyKey":"pay-rest"}
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.payment.paidAmount").value(140000))
        .andExpect(jsonPath("$.payment.remainingAmount").value(0));

    mockMvc
        .perform(
            get("/orders/{orderId}/payments", orderId)
                .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    verify(paymentEventPublisher, atLeast(2)).publish(any(PaymentEvent.class));
  }

  @Test
  void qrRequestRefundAndAdminHistoryWork() throws Exception {
    RegisteredUser owner = register("qr-owner");
    String staffToken = registerStaffAndLogin("qr-staff");
    UUID orderId = submitOrder(owner.accessToken(), "QR");

    mockMvc
        .perform(
            post("/orders/{orderId}/payment-requests/qr", orderId)
                .header("Authorization", "Bearer " + owner.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount":50000}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(
            jsonPath("$.paymentUrl")
                .value(org.hamcrest.Matchers.startsWith("http://pay.local/qr/")));

    MvcResult paymentResult =
        mockMvc
            .perform(
                post("/admin/orders/{orderId}/payments", orderId)
                    .header("Authorization", "Bearer " + staffToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"amount":140000,"method":"QR_CODE","idempotencyKey":"pay-full-qr"}
                        """))
            .andExpect(status().isOk())
            .andReturn();
    String paymentId =
        JsonPath.read(paymentResult.getResponse().getContentAsString(), "$.paymentId");

    mockMvc
        .perform(
            post("/admin/payments/{paymentId}/refunds", paymentId)
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"amount":20000,"idempotencyKey":"refund-partial","reason":"customer request"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.amount").value(20000));

    mockMvc
        .perform(
            get("/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + owner.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payment.paymentStatus").value("PAID"))
        .andExpect(jsonPath("$.payment.refundStatus").value("PARTIALLY_REFUNDED"))
        .andExpect(jsonPath("$.payment.refundedAmount").value(20000));

    mockMvc
        .perform(
            get("/admin/payments")
                .header("Authorization", "Bearer " + staffToken)
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  private RegisteredUser register(String prefix) throws Exception {
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
    String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    return new RegisteredUser(username, email, accessToken);
  }

  private String registerStaffAndLogin(String prefix) throws Exception {
    RegisteredUser registered = register(prefix);
    var user = userRepository.findByEmail(registered.email()).orElseThrow();
    var staff = roleRepository.findByName(RoleEnum.STAFF.getName()).orElseThrow();
    UserRoleEntity userRole = new UserRoleEntity();
    userRole.setId(UUID.randomUUID());
    userRole.setUser(user);
    userRole.setRole(staff);
    userRoleRepository.save(userRole);

    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"username":"%s","password":"secret123"}
                        """
                            .formatted(registered.username())))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
  }

  private UUID submitOrder(String accessToken, String prefix) throws Exception {
    UUID tableId = createTable(prefix);
    MenuFixture menu = createMenu(prefix);
    mockMvc
        .perform(
            post("/cart/items")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"tableId":"%s","dishId":"%s","toppingOptionIds":["%s"],"quantity":2}
                    """
                        .formatted(tableId, menu.dishId(), menu.toppingA())))
        .andExpect(status().isOk());

    MvcResult submitResult =
        mockMvc
            .perform(post("/orders").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(140000))
            .andExpect(jsonPath("$.payment.paymentStatus").value("UNPAID"))
            .andReturn();
    return UUID.fromString(
        JsonPath.read(submitResult.getResponse().getContentAsString(), "$.orderId"));
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

  private record RegisteredUser(String username, String email, String accessToken) {}

  private record MenuFixture(UUID dishId, UUID toppingA) {}
}
