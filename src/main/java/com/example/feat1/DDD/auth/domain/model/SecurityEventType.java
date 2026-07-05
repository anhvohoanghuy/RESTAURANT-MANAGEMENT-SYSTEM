package com.example.feat1.DDD.auth.domain.model;

public enum SecurityEventType {
  LOGIN,
  ACCOUNT_LOCKOUT,
  GOOGLE_LOGIN,
  REFRESH,
  REFRESH_REUSE,
  LOGOUT,
  EMAIL_VERIFY,
  PASSWORD_RESET,
  SESSION_REVOKE
}
