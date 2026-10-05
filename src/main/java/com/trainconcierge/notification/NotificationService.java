package com.trainconcierge.notification;

import com.trainconcierge.auth.AuthService;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.notification.dto.NotificationPageResponse;
import com.trainconcierge.notification.dto.NotificationResponse;
import com.trainconcierge.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core service for Phase 17 — In-App Notification System.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>Outbox pattern</b>: notifications are written as DB rows
 *       ({@code IN_APP} channel by default). Email/push adapters can be
 *       plugged in later without touching this service.</li>
 *   <li><b>Duplicate suppression</b>: each {@code (user, type, referenceId)}
 *       triple is checked before inserting a new row; repeated event
 *       processing never creates a duplicate notification.</li>
 *   <li><b>Ownership enforcement</b>: read/mark-as-read operations verify the
 *       authenticated user owns the notification; cross-user access throws
 *       {@link ForbiddenException}.</li>
 *   <li><b>Separation from delivery</b>: this service only persists
 *       notifications. A future {@code NotificationDeliveryService} (email,
 *       push) can consume rows where {@code sent = false}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AuthService authService;

    // ──────────────────────────────────────────────────────────────────────
    // Public factory / creation methods
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Creates a BOOKING_CONFIRMED notification.
     *
     * @param user            the booking owner
     * @param bookingId       booking DB id (used as referenceId)
     * @param bookingRef      human-readable booking reference
     * @param trainName       train display name
     * @param journeyDate     journey date string
     */
    @Transactional
    public void notifyBookingConfirmed(User user, Long bookingId,
                                       String bookingRef, String trainName,
                                       String journeyDate) {
        String refId = "BOOKING-" + bookingId;
        if (isDuplicate(user, NotificationType.BOOKING_CONFIRMED, refId)) return;

        save(user, NotificationType.BOOKING_CONFIRMED,
                "Booking Confirmed — " + bookingRef,
                "Your seat on " + trainName + " on " + journeyDate +
                        " has been confirmed. Reference: " + bookingRef + ".",
                refId, "Booking");
    }

    /**
     * Creates a BOOKING_CANCELLED notification.
     */
    @Transactional
    public void notifyBookingCancelled(User user, Long bookingId,
                                       String bookingRef, String trainName) {
        String refId = "BOOKING-CANCEL-" + bookingId;
        if (isDuplicate(user, NotificationType.BOOKING_CANCELLED, refId)) return;

        save(user, NotificationType.BOOKING_CANCELLED,
                "Booking Cancelled — " + bookingRef,
                "Your booking " + bookingRef + " on " + trainName +
                        " has been cancelled.",
                refId, "Booking");
    }

    /**
     * Creates a DISRUPTION_ALERT notification.
     */
    @Transactional
    public void notifyDisruptionAlert(User user, Long disruptionId,
                                      String trainName, String scheduleDate,
                                      String reason) {
        String refId = "DISRUPTION-" + disruptionId;
        if (isDuplicate(user, NotificationType.DISRUPTION_ALERT, refId)) return;

        save(user, NotificationType.DISRUPTION_ALERT,
                "Train Disruption — " + trainName,
                "Your train " + trainName + " on " + scheduleDate +
                        " has been disrupted. Reason: " + reason +
                        ". Please check alternative options.",
                refId, "DisruptionEvent");
    }

    /**
     * Creates a REBOOKING_SUGGESTION notification.
     */
    @Transactional
    public void notifyRebookingSuggestion(User user, Long disruptionId,
                                          int alternativeCount) {
        String refId = "REBOOK-SUGGEST-" + disruptionId;
        if (isDuplicate(user, NotificationType.REBOOKING_SUGGESTION, refId)) return;

        save(user, NotificationType.REBOOKING_SUGGESTION,
                "Alternative Trains Available",
                alternativeCount + " alternative train(s) have been found for your disrupted journey. " +
                        "Please review and confirm your preferred option.",
                refId, "DisruptionEvent");
    }

    /**
     * Creates a REBOOKING_CONFIRMED notification.
     */
    @Transactional
    public void notifyRebookingConfirmed(User user, Long rebookingHistoryId,
                                         String newBookingRef, String newTrainName,
                                         String newJourneyDate, String newArrival) {
        String refId = "REBOOKING-" + rebookingHistoryId;
        if (isDuplicate(user, NotificationType.REBOOKING_CONFIRMED, refId)) return;

        save(user, NotificationType.REBOOKING_CONFIRMED,
                "Rebooking Confirmed — " + newBookingRef,
                "Your train has been rebooked to " + newTrainName +
                        " on " + newJourneyDate + ", arriving at " + newArrival +
                        ". New reference: " + newBookingRef + ".",
                refId, "RebookingHistory");
    }

    /**
     * Creates a HOTEL_BOOKING_CONFIRMED notification.
     */
    @Transactional
    public void notifyHotelConfirmed(User user, Long hotelBookingId,
                                     String hotelName, String checkIn,
                                     String checkOut, int nights) {
        String refId = "HOTEL-" + hotelBookingId;
        if (isDuplicate(user, NotificationType.HOTEL_BOOKING_CONFIRMED, refId)) return;

        save(user, NotificationType.HOTEL_BOOKING_CONFIRMED,
                "Hotel Confirmed — " + hotelName,
                "Your hotel booking at " + hotelName + " has been confirmed. " +
                        "Check-in: " + checkIn + " | Check-out: " + checkOut +
                        " (" + nights + " night(s))." +
                        " [Simulated — no real hotel provider was contacted.]",
                refId, "HotelBooking");
    }

    /**
     * Creates a HOTEL_RESCHEDULED notification.
     */
    @Transactional
    public void notifyHotelRescheduled(User user, Long hotelBookingId,
                                       String hotelName, String newCheckIn,
                                       String newCheckOut) {
        String refId = "HOTEL-RESCHEDULE-" + hotelBookingId;
        if (isDuplicate(user, NotificationType.HOTEL_RESCHEDULED, refId)) return;

        save(user, NotificationType.HOTEL_RESCHEDULED,
                "Hotel Rescheduled — " + hotelName,
                "Your hotel check-in at " + hotelName + " has been updated. " +
                        "New check-in: " + newCheckIn + " | New check-out: " + newCheckOut +
                        ". [Simulated — no real hotel provider was contacted.]",
                refId, "HotelBooking");
    }

    /**
     * Creates a HOTEL_BOOKING_CANCELLED notification.
     */
    @Transactional
    public void notifyHotelCancelled(User user, Long hotelBookingId,
                                     String hotelName) {
        String refId = "HOTEL-CANCEL-" + hotelBookingId;
        if (isDuplicate(user, NotificationType.HOTEL_BOOKING_CANCELLED, refId)) return;

        save(user, NotificationType.HOTEL_BOOKING_CANCELLED,
                "Hotel Cancelled — " + hotelName,
                "Your hotel booking at " + hotelName + " has been cancelled. " +
                        "[Simulated — no real hotel provider was contacted.]",
                refId, "HotelBooking");
    }

    /**
     * Creates a CAB_BOOKING_CONFIRMED notification.
     */
    @Transactional
    public void notifyCabConfirmed(User user, Long cabBookingId,
                                   String pickupLocation, String dropoffLocation,
                                   String pickupTime) {
        String refId = "CAB-" + cabBookingId;
        if (isDuplicate(user, NotificationType.CAB_BOOKING_CONFIRMED, refId)) return;

        save(user, NotificationType.CAB_BOOKING_CONFIRMED,
                "Cab Pickup Confirmed",
                "Your cab pickup from " + pickupLocation + " to " + dropoffLocation +
                        " is confirmed for " + pickupTime + "." +
                        " [Simulated — no real cab provider was contacted.]",
                refId, "CabBooking");
    }

    /**
     * Creates a CAB_RESCHEDULED notification.
     */
    @Transactional
    public void notifyCabRescheduled(User user, Long cabBookingId,
                                     String pickupLocation, String newPickupTime) {
        String refId = "CAB-RESCHEDULE-" + cabBookingId;
        if (isDuplicate(user, NotificationType.CAB_RESCHEDULED, refId)) return;

        save(user, NotificationType.CAB_RESCHEDULED,
                "Cab Pickup Rescheduled",
                "Your cab pickup from " + pickupLocation + " has been rescheduled to " +
                        newPickupTime + "." +
                        " [Simulated — no real cab provider was contacted.]",
                refId, "CabBooking");
    }

    /**
     * Creates a CAB_BOOKING_CANCELLED notification.
     */
    @Transactional
    public void notifyCabCancelled(User user, Long cabBookingId,
                                   String pickupLocation) {
        String refId = "CAB-CANCEL-" + cabBookingId;
        if (isDuplicate(user, NotificationType.CAB_BOOKING_CANCELLED, refId)) return;

        save(user, NotificationType.CAB_BOOKING_CANCELLED,
                "Cab Booking Cancelled",
                "Your cab booking from " + pickupLocation + " has been cancelled." +
                        " [Simulated — no real cab provider was contacted.]",
                refId, "CabBooking");
    }

    /**
     * Low-level generic factory — for any type and custom body.
     * Callers outside this service may use this for GENERAL notifications.
     */
    @Transactional
    public Notification createNotification(User user, NotificationType type,
                                           String title, String body,
                                           String referenceId, String referenceType) {
        return save(user, type, title, body, referenceId, referenceType);
    }

    // ──────────────────────────────────────────────────────────────────────
    // User-facing retrieval APIs
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a paginated, newest-first list of notifications for the
     * currently authenticated user.
     *
     * @param page 0-indexed page number
     * @param size page size (max 100)
     */
    @Transactional(readOnly = true)
    public NotificationPageResponse getMyNotifications(int page, int size) {
        User user = authService.getCurrentAuthenticatedUser();
        size = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> pageResult =
                notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        long unreadCount = notificationRepository.countByUserAndReadFalse(user);

        return NotificationPageResponse.builder()
                .notifications(pageResult.getContent().stream()
                        .map(this::toResponse)
                        .toList())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .unreadCount(unreadCount)
                .build();
    }

    /**
     * Returns the count of unread notifications for the authenticated user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        User user = authService.getCurrentAuthenticatedUser();
        return notificationRepository.countByUserAndReadFalse(user);
    }

    /**
     * Marks a single notification as read.
     *
     * <p>Enforces ownership: the authenticated user must own the notification.</p>
     *
     * @param notificationId DB id of the notification
     * @return updated {@link NotificationResponse}
     */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId) {
        User user = authService.getCurrentAuthenticatedUser();

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Access denied: Notification does not belong to your account.");
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
            log.debug("[Notification] Marked id={} as read for userId={}", notificationId, user.getId());
        }

        return toResponse(notification);
    }

    /**
     * Marks all unread notifications for the authenticated user as read.
     *
     * @return number of notifications updated
     */
    @Transactional
    public int markAllAsRead() {
        User user = authService.getCurrentAuthenticatedUser();
        int updated = notificationRepository.markAllReadForUser(user, Instant.now());
        log.info("[Notification] Marked {} notification(s) as read for userId={}", updated, user.getId());
        return updated;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Persists a new {@link Notification} row and returns the saved entity.
     */
    private Notification save(User user, NotificationType type,
                               String title, String body,
                               String referenceId, String referenceType) {
        Notification n = Notification.builder()
                .user(user)
                .type(type)
                .channel(NotificationChannel.IN_APP)
                .title(title)
                .body(body)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .sent(false)
                .read(false)
                .build();
        Notification saved = notificationRepository.save(n);
        log.info("[Notification] Created id={} type={} userId={} refId={}",
                saved.getId(), type, user.getId(), referenceId);
        return saved;
    }

    /**
     * Returns {@code true} if a notification with the same
     * {@code (user, type, referenceId)} already exists, preventing duplicates.
     */
    private boolean isDuplicate(User user, NotificationType type, String referenceId) {
        boolean exists = notificationRepository
                .existsByUserAndTypeAndReferenceId(user, type, referenceId);
        if (exists) {
            log.debug("[Notification] Duplicate suppressed: type={} refId={} userId={}",
                    type, referenceId, user.getId());
        }
        return exists;
    }

    /** Maps a {@link Notification} entity to its response DTO. */
    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .channel(n.getChannel())
                .title(n.getTitle())
                .body(n.getBody())
                .referenceId(n.getReferenceId())
                .referenceType(n.getReferenceType())
                .read(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
