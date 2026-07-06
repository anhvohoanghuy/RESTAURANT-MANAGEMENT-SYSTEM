package com.example.feat1.DDD.inventory_context.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.CategoryRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.DishRequest;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.RecipeRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuStatus;
import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
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
class InventoryCostingIntegrationTest {
  @Autowired private MockMvc mockMvc;
  @Autowired private MenuCatalogService menuCatalogService;

  @Test
  void staffCanManageIngredientCostsAndReadRecipeCosting() throws Exception {
    UUID dishId = createDish("costing");

    MvcResult ingredientResult =
        mockMvc
            .perform(
                post("/admin/inventory/ingredients")
                    .with(user("staff").roles("STAFF"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"name":"Tea %s","baseUnit":"g","description":"Black tea","status":"ACTIVE"}
                        """
                            .formatted(UUID.randomUUID())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.baseUnit").value("g"))
            .andReturn();
    String ingredientId =
        JsonPath.read(ingredientResult.getResponse().getContentAsString(), "$.ingredientId");

    mockMvc
        .perform(
            post("/admin/inventory/ingredients/{ingredientId}/costs", ingredientId)
                .with(user("staff").roles("STAFF"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"unitCost":100000,"costUnit":"kg","source":"supplier-a"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.costUnit").value("kg"));

    menuCatalogService.upsertRecipe(
        new RecipeRequest(
            RecipeTargetType.DISH,
            dishId,
            "Tea recipe",
            List.of(
                new RecipeRequest.Line(
                    UUID.fromString(ingredientId), "Tea", BigDecimal.valueOf(200), "g", 1))));

    mockMvc
        .perform(
            get("/admin/menu/recipes/cost")
                .with(user("staff").roles("STAFF"))
                .param("targetType", "DISH")
                .param("targetId", dishId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullyCosted").value(true))
        .andExpect(jsonPath("$.totalCost").value(20000.00))
        .andExpect(jsonPath("$.lines[0].convertedQuantity").value(0.2));

    mockMvc
        .perform(get("/admin/menu/costing").with(user("staff").roles("STAFF")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].estimatedCost").exists())
        .andExpect(jsonPath("$.items[0].grossMarginAmount").exists());
  }

  @Test
  void userCannotAccessInventoryCostingAndPublicMenuDoesNotExposeCosts() throws Exception {
    mockMvc
        .perform(get("/admin/inventory/ingredients").with(user("user").roles("USER")))
        .andExpect(status().isForbidden());

    createDish("public-costing");

    mockMvc
        .perform(get("/menus/public"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("unitCost"))))
        .andExpect(
            content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("grossMargin"))));
  }

  private UUID createDish(String prefix) {
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
    return dish.id();
  }
}
