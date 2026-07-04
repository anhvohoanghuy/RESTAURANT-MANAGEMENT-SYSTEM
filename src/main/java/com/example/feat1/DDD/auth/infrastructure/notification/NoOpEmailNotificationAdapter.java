package com.example.feat1.DDD.auth.infrastructure.notification;

import com.example.feat1.DDD.auth.application.notification.EmailNotificationPort;
import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NoOpEmailNotificationAdapter implements EmailNotificationPort {
  @Override
  public void sendEmailVerification(User user, String token) {
    log.info("Email verification token generated for user {}", user.getId());
  }

  @Override
  public void sendPasswordReset(User user, String token) {
    log.info("Password reset token generated for user {}", user.getId());
  }
}
