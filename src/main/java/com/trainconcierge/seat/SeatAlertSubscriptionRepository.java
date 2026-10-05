package com.trainconcierge.seat;

import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SeatAlertSubscriptionRepository extends JpaRepository<SeatAlertSubscription, Long> {

    /** All subscriptions (active and inactive) for a user — for "my alerts" view. */
    List<SeatAlertSubscription> findByUserOrderByCreatedAtDesc(User user);

    /** Only active subscriptions for a user. */
    List<SeatAlertSubscription> findByUserAndActiveTrueOrderByCreatedAtDesc(User user);

    /** Find exact duplicate before creating. */
    Optional<SeatAlertSubscription> findByUserAndScheduleAndSeatClass(
            User user, TrainSchedule schedule, SeatClass seatClass);

    /**
     * Used by the scheduler — fetches ALL active, not-yet-triggered subscriptions
     * across all users and schedules in one query (with JOIN FETCH to avoid N+1).
     */
    @Query("""
            SELECT s FROM SeatAlertSubscription s
            JOIN FETCH s.user
            JOIN FETCH s.schedule sc
            JOIN FETCH sc.train
            WHERE s.active = true
            AND s.triggered = false
            """)
    List<SeatAlertSubscription> findAllActiveUntriggered();

    /** Used by alert engine to find active subscriptions for a given schedule+class. */
    List<SeatAlertSubscription> findByScheduleAndSeatClassAndActiveTrueAndTriggeredFalse(
            TrainSchedule schedule, SeatClass seatClass);
}
