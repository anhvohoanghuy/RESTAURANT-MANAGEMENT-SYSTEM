package com.example.feat1.DDD.table_context.infrastructure.presentation;

import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.common.exception.ApiErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
    basePackages = "com.example.feat1.DDD.table_context.infrastructure.presentation")
public class TableExceptionHandler {
  @ExceptionHandler(TableDomainException.class)
  public ResponseEntity<ApiErrorResponse> tableDomain(TableDomainException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiErrorResponse.of(exception.getCode(), exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception) {
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.of("BAD_REQUEST", exception.getMessage()));
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> notFound(EntityNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiErrorResponse.of("NOT_FOUND", exception.getMessage()));
  }
}
