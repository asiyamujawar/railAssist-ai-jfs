package com.trainconcierge.rebooking;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RebookingHistoryRepository extends JpaRepository<RebookingHistory, Long> {

    List<RebookingHistory> findByUser(User user);

    List<RebookingHistory> findByOriginalBooking(Booking originalBooking);

    List<RebookingHistory> findByDisruptionEvent(DisruptionEvent event);

    List<RebookingHistory> findByUserAndStatus(User user, RebookingStatus status);

    List<RebookingHistory> findByJourney(Journey journey);

    @Query("SELECT rh FROM RebookingHistory rh " +
           "JOIN FETCH rh.originalBooking ob " +
           "JOIN FETCH ob.schedule os " +
           "JOIN FETCH os.train ot " +
           "LEFT JOIN FETCH rh.newBooking nb " +
           "LEFT JOIN FETCH nb.schedule ns " +
           "LEFT JOIN FETCH ns.train nt " +
           "LEFT JOIN FETCH rh.disruptionEvent de " +
           "LEFT JOIN FETCH rh.journey j " +
           "WHERE rh.journey = :journey")
    List<RebookingHistory> findByJourneyWithDetails(@Param("journey") Journey journey);

    @Query("SELECT rh FROM RebookingHistory rh " +
           "JOIN FETCH rh.originalBooking ob " +
           "JOIN FETCH ob.schedule os " +
           "JOIN FETCH os.train ot " +
           "LEFT JOIN FETCH rh.newBooking nb " +
           "LEFT JOIN FETCH nb.schedule ns " +
           "LEFT JOIN FETCH ns.train nt " +
           "LEFT JOIN FETCH rh.disruptionEvent de " +
           "LEFT JOIN FETCH rh.journey j " +
           "WHERE rh.id = :id")
    Optional<RebookingHistory> findByIdWithDetails(@Param("id") Long id);

    List<RebookingHistory> findByJourneyId(Long journeyId);

    List<RebookingHistory> findByJourneyAndUser(Journey journey, User user);

    boolean existsByDisruptionEventAndStatus(DisruptionEvent event, RebookingStatus status);
}
