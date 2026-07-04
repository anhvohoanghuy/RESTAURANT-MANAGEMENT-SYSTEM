package com.example.feat1.DDD.auth.application.notification;

import com.example.feat1.DDD.identity_context.domain.model.aggregate.User;

public interface EmailNotificationPort {
  void sendEmailVerification(User user, String token);

  void sendPasswordReset(User user, String token);
}
