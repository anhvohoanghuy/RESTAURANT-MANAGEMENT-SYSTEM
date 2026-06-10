package com.example.feat1.DDD.menu_context.domain.model;

import java.util.List;
import java.util.UUID;

public class Recipe {
  private final UUID id;
  private final RecipeTargetType targetType;
  private final UUID targetId;
  private final String name;
  private final List<RecipeLine> lines;

  public Recipe(
      UUID id, RecipeTargetType targetType, UUID targetId, String name, List<RecipeLine> lines) {
    if (targetType == null) {
      throw new IllegalArgumentException("Recipe target type is required");
    }
    if (targetId == null) {
      throw new IllegalArgumentException("Recipe target id is required");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("Recipe name is required");
    }
    this.id = id;
    this.targetType = targetType;
    this.targetId = targetId;
    this.name = name;
    this.lines = lines == null ? List.of() : List.copyOf(lines);
  }

  public UUID getId() {
    return id;
  }

  public RecipeTargetType getTargetType() {
    return targetType;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public String getName() {
    return name;
  }

  public List<RecipeLine> getLines() {
    return lines;
  }
}
