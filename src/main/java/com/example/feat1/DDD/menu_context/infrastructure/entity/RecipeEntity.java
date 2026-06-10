package com.example.feat1.DDD.menu_context.infrastructure.entity;

import com.example.feat1.DDD.menu_context.domain.model.RecipeTargetType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "recipes")
public class RecipeEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false)
  private RecipeTargetType targetType;

  @Column(name = "target_id", nullable = false)
  private UUID targetId;

  @Column(nullable = false)
  private String name;

  @OneToMany(
      mappedBy = "recipe",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  private List<RecipeLineEntity> lines = new ArrayList<>();
}
