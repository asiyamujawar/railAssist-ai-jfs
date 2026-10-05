package com.trainconcierge.hotel;

import com.trainconcierge.journey.Journey;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelBookingRepository extends JpaRepository<HotelBooking, Long> {

    List<HotelBooking> findByUser(User user);

    List<HotelBooking> findByJourney(Journey journey);

    List<HotelBooking> findByUserAndStatus(User user, HotelBookingStatus status);

    @Query("SELECT hb FROM HotelBooking hb "
            + "JOIN FETCH hb.user u "
            + "LEFT JOIN FETCH hb.journey j "
            + "LEFT JOIN FETCH hb.modifications m "
            + "WHERE hb.id = :id")
    Optional<HotelBooking> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT hb FROM HotelBooking hb "
            + "JOIN FETCH hb.user u "
            + "LEFT JOIN FETCH hb.modifications m "
            + "WHERE hb.journey = :journey "
            + "ORDER BY hb.checkInDate ASC, hb.id ASC")
    List<HotelBooking> findByJourneyOrderByCheckInDateAsc(@Param("journey") Journey journey);

    boolean existsByHotelBookingReference(String hotelBookingReference);
}
