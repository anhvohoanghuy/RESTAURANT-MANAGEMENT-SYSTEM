package com.example.feat1.DDD.identity_context.application.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public enum RoleEnum {
    ADMIN(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "ADMIN"
    ),

    USER(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "USER"
    ),

    MANAGER(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "MANAGER"
    );
    private final UUID id;
    private final String name;
    public static RoleEnum fromName(String name) {
        for (RoleEnum role : values()) {
            if (role.name.equalsIgnoreCase(name)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + name);
    }
}
