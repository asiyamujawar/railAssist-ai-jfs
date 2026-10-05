package com.trainconcierge.seat;

import com.trainconcierge.notification.Notification;
import com.trainconcierge.notification.NotificationChannel;
import com.trainconcierge.notification.NotificationRepository;
import com.trainconcierge.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled polling service that monitors seat availability against user
 * alert thresholds and generates in-app alert records.
 *
 * <p><strong>Design principles:</strong></p>
 * <ul>
 *   <li>Polls on a configurable interval (see {@code seat.alert.poll-interval-ms}).</li>
 *   <li>Never generates random or autonomous seat changes — only reads real DB values.</li>
 *   <li>Duplicate suppression: once a subscription is triggered it is deactivated;
 *       the subscription cannot fire again without the user re-subscribing.</li>
 *   <li>Notification delivery is intentionally separated: this service creates a
 *       {@link Notification} row (outbox); actual delivery (email, push) is
 *       handled by a future NotificationDeliveryService.</li>
 *   <li>Uses DB-backed seat counts only; no external railway APIs are contacted.</li>
 * </ul>
 *
 * <p><strong>Duplicate prevention logic:</strong></p>
 * <ol>
 *   <li>A subscription is marked {@code triggered=true} after the first alert fires.</li>
 *   <li>Triggered subscriptions are excluded from the scheduler query.</li>
 *   <li>Therefore one subscription → at most one alert notification.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatAlertMonitorService {

    private final SeatAlertSubscriptionRepository subscriptionRepository;
    private final SeatAvailabilityRepository availabilityRepository;
    private final SeatAlertHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;

    @Value("${seat.alert.poll-interval-ms:60000}")
    private long pollIntervalMs;

    // ──────────────────────────────────────────────────────────────────────
    // Scheduled entry point
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Runs every {@code seat.alert.poll-interval-ms} milliseconds (default 60 s).
     * Loads all active/untriggered subscriptions, checks each against live seat counts,
     * and fires alert notifications for those that meet their threshold condition.
     *
     * <p>The fixed-delay strategy ensures no concurrent runs overlap even if a
     * poll takes longer than the interval.</p>
     */
    @Scheduled(fixedDelayString = "${seat.alert.poll-interval-ms:60000}")
    @Transactional
    public void runMonitoringCycle() {
        List<SeatAlertSubscription> active = subscriptionRepository.findAllActiveUntriggered();
        if (active.isEmpty()) {
            log.debug("[SeatAlertMonitor] No active subscriptions to evaluate.");
            return;
        }

        log.info("[SeatAlertMonitor] Evaluating {} active subscription(s).", active.size());
        int fired = 0;

        for (SeatAlertSubscription sub : active) {
            try {
                if (checkAndFire(sub)) {
                    fired++;
                }
            } catch (Exception ex) {
                // One failed evaluation must not abort the entire cycle
                log.error("[SeatAlertMonitor] Error evaluating subscription id={}: {}",
                        sub.getId(), ex.getMessage(), ex);
            }
        }

        log.info("[SeatAlertMonitor] Cycle complete — {}/{} alert(s) fired.", fired, active.size());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Core evaluation logic
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Evaluates a single subscription.
     *
     * @return true if an alert was fired, false otherwise
     */
    @Transactional
    public boolean checkAndFire(SeatAlertSubscription sub) {
        Optional<SeatAvailability> saOpt = availabilityRepository
                .findByScheduleAndSeatClass(sub.getSchedule(), sub.getSeatClass());

        if (saOpt.isEmpty()) {
            log.debug("[SeatAlertMonitor] No SeatAvailability row for sub id={} — skipping.", sub.getId());
            return false;
        }

        SeatAvailability sa = saOpt.get();
        int available = sa.getAvailableSeats();
        int threshold = sub.getThreshold();

        // Threshold condition: available seats <= threshold
        boolean conditionMet = available <= threshold;

        if (!conditionMet) {
            log.debug("[SeatAlertMonitor] sub id={} — {} seats available, threshold={} — condition NOT met.",
                    sub.getId(), available, threshold);
            return false;
        }

        // Condition met — fire the alert
        Instant now = Instant.now();
        String trainInfo = sub.getSchedule().getTrain().getTrainNumber()
                + " (" + sub.getSchedule().getTrain().getTrainName() + ")";
        String msg = String.format(
                "Seat alert: %d %s seat(s) available on train %s on %s (threshold ≤ %d).",
                available, sub.getSeatClass(), trainInfo,
                sub.getSchedule().getScheduledDate(), threshold);

        // 1. Append history row (audit log — never modified)
        SeatAlertHistory histEntry = SeatAlertHistory.builder()
                .subscription(sub)
                .user(sub.getUser())
                .availableSeatsAtAlert(available)
                .threshold(threshold)
                .alertedAt(now)
                .message(msg)
                .build();
        historyRepository.save(histEntry);

        // 2. Create in-app Notification (outbox — delivery is separate)
        Notification notification = Notification.builder()
                .user(sub.getUser())
                .type(NotificationType.SEAT_AVAILABLE)
                .channel(NotificationChannel.IN_APP)
                .title("Seat Alert — " + sub.getSeatClass() + " on " + trainInfo)
                .body(msg)
                .referenceId(String.valueOf(sub.getId()))
                .referenceType("SeatAlertSubscription")
                .sent(false)
                .read(false)
                .build();
        notificationRepository.save(notification);

        // 3. Mark subscription as triggered (duplicate suppression — no further alerts)
        sub.setTriggered(true);
        sub.setTriggeredAt(now);
        sub.setLastAlertedAt(now);
        sub.setAlertCount(sub.getAlertCount() + 1);
        sub.setActive(false);   // auto-deactivate so it no longer appears in active queries
        subscriptionRepository.save(sub);

        log.info("[SeatAlertMonitor] Alert FIRED: sub id={} userId={} available={} threshold={}",
                sub.getId(), sub.getUser().getId(), available, threshold);
        return true;
    }
}
