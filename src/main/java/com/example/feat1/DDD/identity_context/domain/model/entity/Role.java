package com.example.feat1.DDD.identity_context.domain.model.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    UUID id;
    String name;

    public static Role create(String name) {
        return new Role(UUID.randomUUID(),name);
    }
}
