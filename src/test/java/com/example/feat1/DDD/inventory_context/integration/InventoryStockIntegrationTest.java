package com.example.feat1.DDD.inventory_context.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class InventoryStockIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Test
  void staffCanRecordMovementsAndInspectStockAndLowStock() throws Exception {
    String ingredientId = createIngredient();

    // Receipt increases stock and records a low-stock threshold.
    mockMvc
        .perform(
            post("/admin/inventory/movements")
                .with(user("staff").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ingredientId":"%s","type":"RECEIPT","quantity":2,"unit":"kg","lowStockThreshold":100}
                    """
                        .formatted(ingredientId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseUnit").value("g"))
        .andExpect(jsonPath("$.baseQuantityDelta").value(2000.0))
        .andExpect(jsonPath("$.resultingBalance").value(2000.0));

    // Waste reduces stock in base unit.
    mockMvc
        .perform(
            post("/admin/inventory/movements")
                .with(user("staff").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ingredientId":"%s","type":"WASTE","quantity":1950,"unit":"g","note":"spoiled"}
                    """
                        .formatted(ingredientId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultingBalance").value(50.0));

    // Single-ingredient stock read shows low-stock state.
    mockMvc
        .perform(
            get("/admin/inventory/ingredients/{ingredientId}/stock", ingredientId)
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quantityOnHand").value(50.0))
        .andExpect(jsonPath("$.lowStock").value(true));

    // Low-stock list includes the ingredient.
    mockMvc
        .perform(get("/admin/inventory/low-stock").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[?(@.ingredientId=='%s')].lowStock".formatted(ingredientId))
                .value(org.hamcrest.Matchers.hasItem(true)));

    // Movement history is available.
    mockMvc
        .perform(
            get("/admin/inventory/movements")
                .param("ingredientId", ingredientId)
                .with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void insufficientOutboundMovementIsRejected() throws Exception {
    String ingredientId = createIngredient();

    mockMvc
        .perform(
            post("/admin/inventory/movements")
                .with(user("staff").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ingredientId":"%s","type":"WASTE","quantity":10,"unit":"g"}
                    """
                        .formatted(ingredientId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVENTORY_STOCK_INSUFFICIENT"));
  }

  @Test
  void ordinaryUserAndAnonymousCannotAccessStockApis() throws Exception {
    mockMvc
        .perform(get("/admin/inventory/stock").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/admin/inventory/low-stock")).andExpect(status().isUnauthorized());
  }

  private String createIngredient() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/admin/inventory/ingredients")
                    .with(user("staff").roles("STAFF"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Sugar %s","baseUnit":"g","description":"White sugar","status":"ACTIVE"}
                        """
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.ingredientId");
  }
}
