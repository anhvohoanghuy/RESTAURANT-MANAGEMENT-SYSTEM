package com.example.feat1.DDD.menu_context.infrastructure.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicCategory;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicDish;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicMenuResponse;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicToppingGroup;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicToppingOption;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicMenuControllerTest {
  private final MenuCatalogService service = Mockito.mock(MenuCatalogService.class);
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new PublicMenuController(service)).build();

  @Test
  void publicMenuReturnsCategoryDishToppingTreeWithoutRecipes() throws Exception {
    when(service.getPublicMenu())
        .thenReturn(
            new PublicMenuResponse(
                List.of(
                    new PublicCategory(
                        UUID.randomUUID(),
                        "Rice",
                        "Meals",
                        1,
                        List.of(
                            new PublicDish(
                                UUID.randomUUID(),
                                "Com Tam",
                                "Broken rice",
                                BigDecimal.valueOf(70000),
                                1,
                                List.of(
                                    new PublicToppingGroup(
                                        UUID.randomUUID(),
                                        "Egg",
                                        0,
                                        1,
                                        1,
                                        List.of(
                                            new PublicToppingOption(
                                                UUID.randomUUID(),
                                                "Sunny egg",
                                                BigDecimal.valueOf(10000),
                                                1))))))))));

    mockMvc
        .perform(get("/menus/public").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("recipe"))))
        .andExpect(jsonPath("$.categories[0].name").value("Rice"))
        .andExpect(jsonPath("$.categories[0].dishes[0].name").value("Com Tam"))
        .andExpect(
            jsonPath("$.categories[0].dishes[0].toppingGroups[0].options[0].name")
                .value("Sunny egg"));
  }
}
