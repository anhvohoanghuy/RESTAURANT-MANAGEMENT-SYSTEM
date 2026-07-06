package com.example.feat1.DDD.table_context.application;

import com.example.feat1.DDD.table_context.application.dto.TableOperationDtos.AvailableTable;
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
import com.example.feat1.DDD.table_context.application.event.TableOperationEvent;
import com.example.feat1.DDD.table_context.domain.model.ReservationStatus;
import com.example.feat1.DDD.table_context.domain.model.TableDomainException;
import com.example.feat1.DDD.table_context.domain.model.TableOccupancyState;
import com.example.feat1.DDD.table_context.domain.model.TableSessionStatus;
import com.example.feat1.DDD.table_context.domain.model.TableStatus;
import com.example.feat1.DDD.table_context.domain.port.TableOperationEventPublisher;
import com.example.feat1.DDD.table_context.infrastructure.entity.DiningTableEntity;
import com.example.feat1.DDD.table_context.infrastructure.entity.TableOccupancyEntity;
import com.example.feat1.DDD.table_context.infrastructure.entity.TableReservationEntity;
import com.example.feat1.DDD.table_context.infrastructure.entity.TableSessionEntity;
import com.example.feat1.DDD.table_context.infrastructure.repository.DiningTableRepository;
import com.example.feat1.DDD.table_context.infrastructure.repository.TableOccupancyRepository;
import com.example.feat1.DDD.table_context.infrastructure.repository.TableReservationRepository;
import com.example.feat1.DDD.table_context.infrastructure.repository.TableSessionRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class TableOperationService {
  private static final List<ReservationStatus> ACTIVE_RESERVATION_STATUSES =
      List.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.SEATED);

  private final DiningTableRepository tableRepository;
  private final TableSessionRepository sessionRepository;
  private final TableOccupancyRepository occupancyRepository;
  private final TableReservationRepository reservationRepository;
  private final TableOperationEventPublisher eventPublisher;

  @Transactional
  public TableSessionResponse openSession(
      UUID tableId, OpenTableSessionRequest request, UUID actorUserId) {
    DiningTableEntity table = requireOrderableTable(tableId);
    if (sessionRepository.existsByTableIdAndStatus(tableId, TableSessionStatus.OPEN)) {
      throw TableDomainException.tableSessionAlreadyOpen();
    }

    TableSessionEntity session = new TableSessionEntity();
    applyTableSnapshot(session, table);
    session.setStatus(TableSessionStatus.OPEN);
    session.setPartySize(validPartySize(request == null ? null : request.partySize(), false));
    session.setNote(blankToNull(request == null ? null : request.note()));
    session.setReservationId(request == null ? null : request.reservationId());
    session.setOpenedAt(Instant.now());
    session.setOpenedBy(actorUserId);
    TableSessionEntity saved = sessionRepository.save(session);
    setOccupancyInternal(tableId, TableOccupancyState.OCCUPIED, "SESSION_OPENED", actorUserId);
    publishAfterCommit(
        event(
            TableOperationEvent.TABLE_SESSION_OPENED,
            tableId,
            saved.getId(),
            saved.getReservationId(),
            actorUserId,
            saved.getStatus().name(),
            null));
    return toSessionResponse(saved);
  }

  @Transactional
  public TableSessionResponse closeSession(
      UUID sessionId, CloseTableSessionRequest request, UUID actorUserId) {
    TableSessionEntity session = requireOpenSession(sessionId);
    TableOccupancyState nextState =
        request == null || request.nextState() == null
            ? TableOccupancyState.AVAILABLE
            : request.nextState();
    if (nextState != TableOccupancyState.AVAILABLE && nextState != TableOccupancyState.CLEANING) {
      throw TableDomainException.occupancyTransitionInvalid();
    }
    session.setStatus(TableSessionStatus.CLOSED);
    session.setClosedAt(Instant.now());
    session.setClosedBy(actorUserId);
    if (request != null && request.note() != null && !request.note().isBlank()) {
      session.setNote(request.note().trim());
    }
    TableSessionEntity saved = sessionRepository.save(session);
    setOccupancyInternal(session.getTableId(), nextState, "SESSION_CLOSED", actorUserId);
    publishAfterCommit(
        event(
            TableOperationEvent.TABLE_SESSION_CLOSED,
            saved.getTableId(),
            saved.getId(),
            saved.getReservationId(),
            actorUserId,
            saved.getStatus().name(),
            null));
    return toSessionResponse(saved);
  }

  @Transactional
  public TableSessionResponse cancelSession(
      UUID sessionId, CancelTableSessionRequest request, UUID actorUserId) {
    TableSessionEntity session = requireOpenSession(sessionId);
    session.setStatus(TableSessionStatus.CANCELLED);
    session.setCancelledAt(Instant.now());
    session.setCancelledBy(actorUserId);
    if (request != null && request.reason() != null && !request.reason().isBlank()) {
      session.setNote(request.reason().trim());
    }
    TableSessionEntity saved = sessionRepository.save(session);
    setOccupancyInternal(
        session.getTableId(), TableOccupancyState.AVAILABLE, "SESSION_CANCELLED", actorUserId);
    publishAfterCommit(
        event(
            TableOperationEvent.TABLE_SESSION_CLOSED,
            saved.getTableId(),
            saved.getId(),
            saved.getReservationId(),
            actorUserId,
            saved.getStatus().name(),
            "CANCELLED"));
    return toSessionResponse(saved);
  }

  @Transactional
  public TableOccupancyResponse setOccupancy(
      UUID tableId, SetTableOccupancyRequest request, UUID actorUserId) {
    DiningTableEntity table = requireOrderableTable(tableId);
    TableOccupancyState state = request == null ? null : request.state();
    if (state == null) {
      throw TableDomainException.occupancyTransitionInvalid();
    }
    if (state == TableOccupancyState.OCCUPIED
        && sessionRepository.findByTableIdAndStatus(tableId, TableSessionStatus.OPEN).isEmpty()) {
      throw TableDomainException.occupancyTransitionInvalid();
    }
    setOccupancyInternal(tableId, state, request.reason(), actorUserId);
    publishAfterCommit(
        event(
            TableOperationEvent.TABLE_OCCUPANCY_CHANGED,
            tableId,
            null,
            null,
            actorUserId,
            state.name(),
            request.reason()));
    return toOccupancyResponse(table, Optional.empty(), Optional.empty(), state);
  }

  @Transactional(readOnly = true)
  public List<TableOccupancyResponse> listOccupancy() {
    List<DiningTableEntity> tables = activeTables();
    List<UUID> tableIds = tables.stream().map(DiningTableEntity::getId).toList();
    Map<UUID, TableSessionEntity> sessions =
        sessionRepository.findByTableIdInAndStatus(tableIds, TableSessionStatus.OPEN).stream()
            .collect(Collectors.toMap(TableSessionEntity::getTableId, Function.identity()));
    Map<UUID, TableOccupancyEntity> occupancy =
        occupancyRepository.findByTableIdIn(tableIds).stream()
            .collect(Collectors.toMap(TableOccupancyEntity::getTableId, Function.identity()));
    Instant now = Instant.now();
    Map<UUID, TableReservationEntity> reservations =
        reservationRepository
            .findOverlappingForTables(
                tableIds, now, now.plusSeconds(60), ACTIVE_RESERVATION_STATUSES)
            .stream()
            .collect(
                Collectors.toMap(
                    TableReservationEntity::getTableId, Function.identity(), (a, b) -> a));
    return tables.stream()
        .map(
            table ->
                toOccupancyResponse(
                    table,
                    Optional.ofNullable(sessions.get(table.getId())),
                    Optional.ofNullable(reservations.get(table.getId())),
                    effectiveState(
                        Optional.ofNullable(sessions.get(table.getId())),
                        Optional.ofNullable(occupancy.get(table.getId())),
                        Optional.ofNullable(reservations.get(table.getId())))))
        .toList();
  }

  @Transactional
  public TableReservationResponse createReservation(
      CreateReservationRequest request, UUID actorUserId) {
    validateWindow(
        request == null ? null : request.startTime(), request == null ? null : request.endTime());
    DiningTableEntity table = requireOrderableTable(request.tableId());
    int partySize = validPartySize(request.partySize(), true);
    if (hasActiveOverlap(table.getId(), request.startTime(), request.endTime(), null)) {
      throw TableDomainException.reservationOverlap();
    }

    TableReservationEntity reservation = new TableReservationEntity();
    applyTableSnapshot(reservation, table);
    reservation.setCustomerName(requiredText(request.customerName(), "Customer name is required"));
    reservation.setCustomerPhone(
        requiredText(request.customerPhone(), "Customer phone is required"));
    reservation.setCustomerEmail(blankToNull(request.customerEmail()));
    reservation.setPartySize(partySize);
    reservation.setStartTime(request.startTime());
    reservation.setEndTime(request.endTime());
    reservation.setStatus(ReservationStatus.PENDING);
    reservation.setNote(blankToNull(request.note()));
    reservation.setCreatedBy(actorUserId);
    reservation.setCreatedAt(Instant.now());
    TableReservationEntity saved = reservationRepository.save(reservation);
    publishAfterCommit(
        event(
            TableOperationEvent.RESERVATION_CREATED,
            table.getId(),
            null,
            saved.getId(),
            actorUserId,
            saved.getStatus().name(),
            null));
    return toReservationResponse(saved);
  }

  @Transactional
  public TableReservationResponse updateReservationStatus(
      UUID reservationId, UpdateReservationStatusRequest request, UUID actorUserId) {
    TableReservationEntity reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(TableDomainException::reservationNotFound);
    ReservationStatus target = request == null ? null : request.status();
    if (!isValidTransition(reservation.getStatus(), target)) {
      throw TableDomainException.reservationStatusInvalid();
    }
    reservation.setStatus(target);
    reservation.setUpdatedBy(actorUserId);
    reservation.setUpdatedAt(Instant.now());
    if (request.note() != null && !request.note().isBlank()) {
      reservation.setNote(request.note().trim());
    }
    TableReservationEntity saved = reservationRepository.save(reservation);
    publishAfterCommit(
        event(
            TableOperationEvent.RESERVATION_STATUS_CHANGED,
            saved.getTableId(),
            null,
            saved.getId(),
            actorUserId,
            saved.getStatus().name(),
            null));
    return toReservationResponse(saved);
  }

  @Transactional
  public TableSessionResponse seatReservation(UUID reservationId, UUID actorUserId) {
    TableReservationEntity reservation =
        reservationRepository
            .findById(reservationId)
            .orElseThrow(TableDomainException::reservationNotFound);
    if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
      throw TableDomainException.reservationStatusInvalid();
    }
    if (sessionRepository.existsByTableIdAndStatus(
        reservation.getTableId(), TableSessionStatus.OPEN)) {
      throw TableDomainException.tableSessionAlreadyOpen();
    }
    reservation.setStatus(ReservationStatus.SEATED);
    reservation.setUpdatedBy(actorUserId);
    reservation.setUpdatedAt(Instant.now());
    reservationRepository.save(reservation);
    return openSession(
        reservation.getTableId(),
        new OpenTableSessionRequest(
            reservation.getPartySize(), reservation.getNote(), reservation.getId()),
        actorUserId);
  }

  @Transactional(readOnly = true)
  public TableAvailabilityResponse availability(Instant from, Instant to, Integer partySize) {
    validateWindow(from, to);
    int minimumPartySize = partySize == null ? 1 : validPartySize(partySize, true);
    List<DiningTableEntity> candidates =
        activeTables().stream()
            .filter(table -> table.getCapacity() == null || table.getCapacity() >= minimumPartySize)
            .toList();
    List<UUID> tableIds = candidates.stream().map(DiningTableEntity::getId).toList();
    Collection<UUID> occupied =
        sessionRepository.findByTableIdInAndStatus(tableIds, TableSessionStatus.OPEN).stream()
            .map(TableSessionEntity::getTableId)
            .collect(Collectors.toSet());
    Collection<UUID> reserved =
        reservationRepository
            .findOverlappingForTables(tableIds, from, to, ACTIVE_RESERVATION_STATUSES)
            .stream()
            .map(TableReservationEntity::getTableId)
            .collect(Collectors.toSet());
    Map<UUID, TableOccupancyEntity> occupancy =
        occupancyRepository.findByTableIdIn(tableIds).stream()
            .collect(Collectors.toMap(TableOccupancyEntity::getTableId, Function.identity()));

    List<AvailableTable> available =
        candidates.stream()
            .filter(table -> !occupied.contains(table.getId()))
            .filter(table -> !reserved.contains(table.getId()))
            .filter(
                table ->
                    occupancy.getOrDefault(table.getId(), new TableOccupancyEntity()).getState()
                        != TableOccupancyState.OUT_OF_SERVICE)
            .map(
                table ->
                    new AvailableTable(
                        table.getId(),
                        table.getCode(),
                        table.getName(),
                        table.getArea().getId(),
                        table.getArea().getName(),
                        table.getCapacity(),
                        occupancy
                            .getOrDefault(table.getId(), defaultOccupancy(table.getId()))
                            .getState()))
            .toList();
    return new TableAvailabilityResponse(from, to, available);
  }

  @Transactional(readOnly = true)
  public TableSessionResponse validateOpenSession(UUID sessionId, UUID tableId) {
    if (sessionId == null) {
      return null;
    }
    TableSessionEntity session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(TableDomainException::tableSessionNotFound);
    if (session.getStatus() != TableSessionStatus.OPEN || !session.getTableId().equals(tableId)) {
      throw TableDomainException.tableSessionNotOpen();
    }
    return toSessionResponse(session);
  }

  private DiningTableEntity requireOrderableTable(UUID tableId) {
    if (tableId == null) {
      throw TableDomainException.tableNotOrderable();
    }
    DiningTableEntity table =
        tableRepository.findById(tableId).orElseThrow(TableDomainException::tableNotOrderable);
    if (table.getStatus() != TableStatus.ACTIVE
        || table.getArea().getStatus() != TableStatus.ACTIVE) {
      throw TableDomainException.tableNotOrderable();
    }
    return table;
  }

  private List<DiningTableEntity> activeTables() {
    return tableRepository.findAllByOrderBySortOrderAscCodeAsc().stream()
        .filter(table -> table.getStatus() == TableStatus.ACTIVE)
        .filter(table -> table.getArea().getStatus() == TableStatus.ACTIVE)
        .sorted(Comparator.comparing(DiningTableEntity::getCode))
        .toList();
  }

  private TableSessionEntity requireOpenSession(UUID sessionId) {
    TableSessionEntity session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(TableDomainException::tableSessionNotFound);
    if (session.getStatus() != TableSessionStatus.OPEN) {
      throw TableDomainException.tableSessionNotOpen();
    }
    return session;
  }

  private void applyTableSnapshot(TableSessionEntity session, DiningTableEntity table) {
    session.setTableId(table.getId());
    session.setTableCode(table.getCode());
    session.setTableName(table.getName());
    session.setAreaId(table.getArea().getId());
    session.setAreaName(table.getArea().getName());
  }

  private void applyTableSnapshot(TableReservationEntity reservation, DiningTableEntity table) {
    reservation.setTableId(table.getId());
    reservation.setTableCode(table.getCode());
    reservation.setTableName(table.getName());
    reservation.setAreaId(table.getArea().getId());
    reservation.setAreaName(table.getArea().getName());
  }

  private TableOccupancyEntity setOccupancyInternal(
      UUID tableId, TableOccupancyState state, String reason, UUID actorUserId) {
    TableOccupancyEntity occupancy =
        occupancyRepository.findByTableId(tableId).orElseGet(TableOccupancyEntity::new);
    occupancy.setTableId(tableId);
    occupancy.setState(state);
    occupancy.setReason(blankToNull(reason));
    occupancy.setUpdatedBy(actorUserId);
    occupancy.setUpdatedAt(Instant.now());
    return occupancyRepository.save(occupancy);
  }

  private TableOccupancyState effectiveState(
      Optional<TableSessionEntity> session,
      Optional<TableOccupancyEntity> occupancy,
      Optional<TableReservationEntity> reservation) {
    if (session.isPresent()) {
      return TableOccupancyState.OCCUPIED;
    }
    if (occupancy.map(TableOccupancyEntity::getState).orElse(TableOccupancyState.AVAILABLE)
        == TableOccupancyState.OUT_OF_SERVICE) {
      return TableOccupancyState.OUT_OF_SERVICE;
    }
    if (reservation.isPresent()) {
      return TableOccupancyState.RESERVED;
    }
    return occupancy.map(TableOccupancyEntity::getState).orElse(TableOccupancyState.AVAILABLE);
  }

  private TableOccupancyEntity defaultOccupancy(UUID tableId) {
    TableOccupancyEntity occupancy = new TableOccupancyEntity();
    occupancy.setTableId(tableId);
    occupancy.setState(TableOccupancyState.AVAILABLE);
    return occupancy;
  }

  private boolean hasActiveOverlap(
      UUID tableId, Instant startTime, Instant endTime, UUID ignoreId) {
    return reservationRepository
        .findOverlapping(tableId, startTime, endTime, ACTIVE_RESERVATION_STATUSES)
        .stream()
        .anyMatch(reservation -> !reservation.getId().equals(ignoreId));
  }

  private boolean isValidTransition(ReservationStatus current, ReservationStatus target) {
    if (target == null || current == null) {
      return false;
    }
    return switch (current) {
      case PENDING ->
          target == ReservationStatus.CONFIRMED || target == ReservationStatus.CANCELLED;
      case CONFIRMED ->
          target == ReservationStatus.SEATED
              || target == ReservationStatus.CANCELLED
              || target == ReservationStatus.NO_SHOW;
      case SEATED -> target == ReservationStatus.COMPLETED;
      case CANCELLED, NO_SHOW, COMPLETED -> false;
    };
  }

  private void validateWindow(Instant startTime, Instant endTime) {
    if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
      throw TableDomainException.availabilityWindowInvalid();
    }
  }

  private int validPartySize(Integer partySize, boolean required) {
    if (partySize == null) {
      if (required) {
        throw new IllegalArgumentException("Party size is required");
      }
      return 0;
    }
    if (partySize <= 0) {
      throw new IllegalArgumentException("Party size must be positive");
    }
    return partySize;
  }

  private String requiredText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private TableSessionResponse toSessionResponse(TableSessionEntity session) {
    return new TableSessionResponse(
        session.getId(),
        session.getTableId(),
        session.getTableCode(),
        session.getTableName(),
        session.getAreaId(),
        session.getAreaName(),
        session.getStatus(),
        session.getPartySize(),
        session.getNote(),
        session.getReservationId(),
        session.getOpenedAt(),
        session.getClosedAt(),
        session.getCancelledAt());
  }

  private TableReservationResponse toReservationResponse(TableReservationEntity reservation) {
    return new TableReservationResponse(
        reservation.getId(),
        reservation.getTableId(),
        reservation.getTableCode(),
        reservation.getTableName(),
        reservation.getAreaId(),
        reservation.getAreaName(),
        reservation.getCustomerName(),
        reservation.getCustomerPhone(),
        reservation.getCustomerEmail(),
        reservation.getPartySize(),
        reservation.getStartTime(),
        reservation.getEndTime(),
        reservation.getStatus(),
        reservation.getNote(),
        reservation.getCreatedAt(),
        reservation.getUpdatedAt());
  }

  private TableOccupancyResponse toOccupancyResponse(
      DiningTableEntity table,
      Optional<TableSessionEntity> session,
      Optional<TableReservationEntity> reservation,
      TableOccupancyState state) {
    return new TableOccupancyResponse(
        table.getId(),
        table.getCode(),
        table.getName(),
        table.getArea().getId(),
        table.getArea().getName(),
        table.getCapacity(),
        state,
        session.map(this::toSessionResponse).orElse(null),
        reservation.map(this::toReservationResponse).orElse(null));
  }

  private TableOperationEvent event(
      String eventType,
      UUID tableId,
      UUID sessionId,
      UUID reservationId,
      UUID actorUserId,
      String state,
      String reason) {
    return new TableOperationEvent(
        UUID.randomUUID(),
        eventType,
        Instant.now(),
        tableId,
        sessionId,
        reservationId,
        actorUserId,
        state,
        reason);
  }

  private void publishAfterCommit(TableOperationEvent event) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      eventPublisher.publish(event);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            eventPublisher.publish(event);
          }
        });
  }
}
