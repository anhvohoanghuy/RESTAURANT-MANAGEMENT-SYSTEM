package com.example.feat1.DDD.auth.infrastructure.security;

import com.example.feat1.common.exception.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final ObjectMapper objectMapper;

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/auth/register",
                        "/auth/login",
                        "/auth/google",
                        "/auth/refresh",
                        "/auth/logout",
                        "/auth/email/verification/request",
                        "/auth/email/verify",
                        "/auth/password/forgot",
                        "/auth/password/reset")
                    .permitAll()
                    .requestMatchers("/api/auth/**")
                    .permitAll()
                    .requestMatchers(
                        "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                    .permitAll()
                    .requestMatchers("/auth/sessions", "/auth/sessions/**")
                    .hasAnyRole("USER", "ADMIN", "STAFF")
                    .requestMatchers("/cart", "/cart/**")
                    .hasAnyRole("USER", "ADMIN", "STAFF")
                    .requestMatchers("/orders", "/orders/**")
                    .hasAnyRole("USER", "ADMIN", "STAFF")
                    .requestMatchers(HttpMethod.GET, "/menus/public")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/tables/public")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/tables/public/availability")
                    .permitAll()
                    .requestMatchers("/admin/payments", "/admin/payments/**", "/admin/orders/**")
                    .hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers(
                        "/admin/inventory/**", "/admin/menu/recipes/cost", "/admin/menu/costing")
                    .hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers(
                        "/admin/tables/occupancy",
                        "/admin/tables/availability",
                        "/admin/tables/*/sessions",
                        "/admin/table-sessions/**",
                        "/admin/tables/*/occupancy",
                        "/admin/tables/reservations",
                        "/admin/tables/reservations/**")
                    .hasAnyRole("ADMIN", "STAFF")
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/users/**")
                    .hasAnyRole("USER", "ADMIN", "STAFF")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) ->
                            writeError(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                ApiErrorResponse.of(
                                    "UNAUTHENTICATED", "Authentication is required")))
                    .accessDeniedHandler(
                        (request, response, exception) ->
                            writeError(
                                response,
                                HttpStatus.FORBIDDEN,
                                ApiErrorResponse.of("FORBIDDEN", "Access is denied"))))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void writeError(
      HttpServletResponse response, HttpStatus status, ApiErrorResponse errorResponse)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), errorResponse);
  }
}
