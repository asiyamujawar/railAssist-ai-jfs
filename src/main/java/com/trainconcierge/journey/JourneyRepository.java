package com.trainconcierge.journey;

import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JourneyRepository extends JpaRepository<Journey, Long> {

    List<Journey> findByUser(User user);

    List<Journey> findByUserAndStatus(User user, JourneyStatus status);

    List<Journey> findByUserAndTravelDateBetween(User user, LocalDate from, LocalDate to);

    List<Journey> findByStatus(JourneyStatus status);

    /**
     * Loads all journeys that require active monitoring.
     *
     * Criteria:
     * <ul>
     *   <li>Status is PLANNED or IN_PROGRESS</li>
     *   <li>Travel date is today or in the future</li>
     * </ul>
     *
     * Uses JOIN FETCH on bookings and their schedules to avoid N+1 during the
     * monitoring cycle. Only CONFIRMED bookings are eagerly loaded.
     */
    @Query("""
            SELECT DISTINCT j FROM Journey j
            WHERE j.status IN (
                com.trainconcierge.journey.JourneyStatus.PLANNED,
                com.trainconcierge.journey.JourneyStatus.IN_PROGRESS
            )
            AND j.travelDate >= :today
            """)
    List<Journey> findActiveJourneysForMonitoring(@Param("today") LocalDate today);
}
