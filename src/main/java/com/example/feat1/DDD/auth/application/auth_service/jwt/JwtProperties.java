package com.example.feat1.DDD.auth.application.auth_service.jwt;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
  private long accessExpiration;
  private long refreshExpiration;
  private String secret;
}
