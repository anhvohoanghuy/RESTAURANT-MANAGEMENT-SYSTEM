package com.example.feat1.DDD.menu_context.infrastructure.presentation;

import com.example.feat1.DDD.menu_context.application.MenuCatalogService;
import com.example.feat1.DDD.menu_context.application.dto.MenuDtos.PublicMenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class PublicMenuController {
  private final MenuCatalogService menuCatalogService;

  @GetMapping("/public")
  public ResponseEntity<PublicMenuResponse> getPublicMenu() {
    return ResponseEntity.ok(menuCatalogService.getPublicMenu());
  }
}
