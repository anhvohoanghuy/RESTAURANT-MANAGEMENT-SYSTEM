package com.example.feat1.DDD.table_context.infrastructure.presentation;

import com.example.feat1.DDD.auth.infrastructure.security.CustomUserDetails;
import com.example.feat1.DDD.table_context.application.TableOperationService;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.CancelTableSessionRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.CloseTableSessionRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.CreateReservationRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.OpenTableSessionRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.SetTableOccupancyRequest;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.TableAvailabilityResponse;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.TableOccupancyResponse;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.TableReservationResponse;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.TableSessionResponse;
import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.UpdateReservationStatusRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TableOperationController {
  private final TableOperationService tableOperationService;

  @PostMapping("/admin/tables/{tableId}/sessions")
  public ResponseEntity<TableSessionResponse> openSession(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID tableId,
      @RequestBody(required = false) OpenTableSessionRequest request) {
    return ResponseEntity.ok(
        tableOperationService.openSession(tableId, request, principal.getId()));
  }

  @PostMapping("/admin/table-sessions/{sessionId}/close")
  public ResponseEntity<TableSessionResponse> closeSession(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID sessionId,
      @RequestBody(required = false) CloseTableSessionRequest request) {
    return ResponseEntity.ok(
        tableOperationService.closeSession(sessionId, request, principal.getId()));
  }

  @PostMapping("/admin/table-sessions/{sessionId}/cancel")
  public ResponseEntity<TableSessionResponse> cancelSession(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID sessionId,
      @RequestBody(required = false) CancelTableSessionRequest request) {
    return ResponseEntity.ok(
        tableOperationService.cancelSession(sessionId, request, principal.getId()));
  }

  @PatchMapping("/admin/tables/{tableId}/occupancy")
  public ResponseEntity<TableOccupancyResponse> setOccupancy(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID tableId,
      @RequestBody SetTableOccupancyRequest request) {
    return ResponseEntity.ok(
        tableOperationService.setOccupancy(tableId, request, principal.getId()));
  }

  @GetMapping("/admin/tables/occupancy")
  public ResponseEntity<List<TableOccupancyResponse>> listOccupancy() {
    return ResponseEntity.ok(tableOperationService.listOccupancy());
  }

  @GetMapping("/admin/tables/availability")
  public ResponseEntity<TableAvailabilityResponse> adminAvailability(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) Integer partySize) {
    return ResponseEntity.ok(tableOperationService.availability(from, to, partySize));
  }

  @GetMapping("/tables/public/availability")
  public ResponseEntity<TableAvailabilityResponse> publicAvailability(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) Integer partySize) {
    return ResponseEntity.ok(tableOperationService.availability(from, to, partySize));
  }

  @PostMapping("/admin/tables/reservations")
  public ResponseEntity<TableReservationResponse> createReservation(
      @AuthenticationPrincipal CustomUserDetails principal,
      @RequestBody CreateReservationRequest request) {
    return ResponseEntity.ok(tableOperationService.createReservation(request, principal.getId()));
  }

  @PatchMapping("/admin/tables/reservations/{reservationId}/status")
  public ResponseEntity<TableReservationResponse> updateReservationStatus(
      @AuthenticationPrincipal CustomUserDetails principal,
      @PathVariable UUID reservationId,
      @RequestBody UpdateReservationStatusRequest request) {
    return ResponseEntity.ok(
        tableOperationService.updateReservationStatus(reservationId, request, principal.getId()));
  }

  @PostMapping("/admin/tables/reservations/{reservationId}/seat")
  public ResponseEntity<TableSessionResponse> seatReservation(
      @AuthenticationPrincipal CustomUserDetails principal, @PathVariable UUID reservationId) {
    return ResponseEntity.ok(
        tableOperationService.seatReservation(reservationId, principal.getId()));
  }
}
