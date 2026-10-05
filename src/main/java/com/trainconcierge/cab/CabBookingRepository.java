package com.trainconcierge.cab;

import com.trainconcierge.journey.Journey;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CabBookingRepository extends JpaRepository<CabBooking, Long> {

    List<CabBooking> findByUser(User user);

    List<CabBooking> findByJourney(Journey journey);

    List<CabBooking> findByUserAndStatus(User user, CabBookingStatus status);

    @Query("SELECT cb FROM CabBooking cb "
            + "JOIN FETCH cb.user u "
            + "LEFT JOIN FETCH cb.journey j "
            + "LEFT JOIN FETCH cb.modifications m "
            + "WHERE cb.id = :id")
    Optional<CabBooking> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT cb FROM CabBooking cb "
            + "JOIN FETCH cb.user u "
            + "LEFT JOIN FETCH cb.modifications m "
            + "WHERE cb.journey = :journey "
            + "ORDER BY cb.scheduledPickupTime ASC, cb.id ASC")
    List<CabBooking> findByJourneyOrderByScheduledPickupTimeAsc(@Param("journey") Journey journey);

    boolean existsByCabBookingReference(String cabBookingReference);
}
