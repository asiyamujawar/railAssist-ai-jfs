package com.trainconcierge.seat;

import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.dto.CreateSeatAlertRequest;
import com.trainconcierge.seat.dto.SeatAlertSubscriptionResponse;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Manages seat alert subscription lifecycle.
 *
 * Responsibilities:
 * <ul>
 *   <li>Create a new subscription (one active per user/schedule/class)</li>
 *   <li>Deactivate a subscription</li>
 *   <li>Return a user's subscriptions (all or active-only)</li>
 *   <li>Enforce ownership — a user can only manage their own alerts</li>
 * </ul>
 *
 * Alert generation is handled separately by {@link SeatAlertMonitorService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatAlertSubscriptionService {

    private final SeatAlertSubscriptionRepository alertRepository;
    private final TrainScheduleRepository scheduleRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final UserRepository userRepository;

    // ──────────────────────────────────────────────────────────────────────
    // Create
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public SeatAlertSubscriptionResponse subscribe(String email,
                                                    CreateSeatAlertRequest request) {
        User user = loadUser(email);
        TrainSchedule schedule = loadSchedule(request.getScheduleId());

        // Duplicate guard — one active subscription per (user, schedule, seatClass)
        alertRepository.findByUserAndScheduleAndSeatClass(user, schedule, request.getSeatClass())
                .ifPresent(existing -> {
                    throw new BadRequestException(
                            "You already have an alert subscription for this schedule and seat class. " +
                            "Deactivate the existing one before creating a new one.",
                            ErrorCode.SEAT_ALERT_ALREADY_EXISTS);
                });

        SeatAlertSubscription sub = SeatAlertSubscription.builder()
                .user(user)
                .schedule(schedule)
                .seatClass(request.getSeatClass())
                .threshold(request.getThreshold())
                .active(true)
                .triggered(false)
                .alertCount(0)
                .build();

        SeatAlertSubscription saved = alertRepository.save(sub);
        log.info("Seat alert created: userId={} scheduleId={} class={} threshold={}",
                user.getId(), schedule.getId(), request.getSeatClass(), request.getThreshold());

        return toResponse(saved);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Read
    // ──────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SeatAlertSubscriptionResponse> getMyAlerts(String email) {
        User user = loadUser(email);
        return alertRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Deactivate
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public SeatAlertSubscriptionResponse deactivate(String email, Long alertId) {
        User user = loadUser(email);
        SeatAlertSubscription sub = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SeatAlertSubscription", "id", alertId));

        if (!sub.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("You do not have permission to modify this alert subscription.");
        }

        if (!sub.isActive()) {
            throw new BadRequestException(
                    "This alert subscription is already inactive.",
                    ErrorCode.SEAT_ALERT_ALREADY_INACTIVE);
        }

        sub.setActive(false);
        alertRepository.save(sub);
        log.info("Seat alert deactivated: id={} userId={}", alertId, user.getId());
        return toResponse(sub);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal — called by SeatAlertMonitorService after alert fires
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public void markTriggered(SeatAlertSubscription sub) {
        sub.setTriggered(true);
        sub.setActive(false);         // auto-deactivate once triggered
        alertRepository.save(sub);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    private User loadUser(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private TrainSchedule loadSchedule(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainSchedule", "id", scheduleId));
    }

    SeatAlertSubscriptionResponse toResponse(SeatAlertSubscription sub) {
        // Live seat count for this class — may be absent if no row yet
        Optional<SeatAvailability> sa = seatAvailabilityRepository
                .findByScheduleAndSeatClass(sub.getSchedule(), sub.getSeatClass());

        return SeatAlertSubscriptionResponse.builder()
                .id(sub.getId())
                .scheduleId(sub.getSchedule().getId())
                .trainNumber(sub.getSchedule().getTrain().getTrainNumber())
                .trainName(sub.getSchedule().getTrain().getTrainName())
                .scheduledDate(sub.getSchedule().getScheduledDate())
                .seatClass(sub.getSeatClass())
                .threshold(sub.getThreshold())
                .active(sub.isActive())
                .triggered(sub.isTriggered())
                .alertCount(sub.getAlertCount())
                .triggeredAt(sub.getTriggeredAt())
                .lastAlertedAt(sub.getLastAlertedAt())
                .currentAvailableSeats(sa.map(SeatAvailability::getAvailableSeats).orElse(null))
                .createdAt(sub.getCreatedAt())
                .updatedAt(sub.getUpdatedAt())
                .build();
    }
}
