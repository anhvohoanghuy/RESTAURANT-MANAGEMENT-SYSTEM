package com.example.feat1.DDD.identity_context.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class rolePermision {
    private UUID id;
    private Role role;
    private Permission permission;
}