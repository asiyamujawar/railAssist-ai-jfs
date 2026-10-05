package com.trainconcierge.seat;

import com.trainconcierge.schedule.TrainSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface SeatAvailabilityRepository extends JpaRepository<SeatAvailability, Long> {

    Optional<SeatAvailability> findByScheduleAndSeatClass(
            TrainSchedule schedule, SeatClass seatClass);

    List<SeatAvailability> findBySchedule(TrainSchedule schedule);

    List<SeatAvailability> findByScheduleAndAvailableSeatsGreaterThan(
            TrainSchedule schedule, int minSeats);

    @Query("SELECT sa FROM SeatAvailability sa " +
           "JOIN FETCH sa.schedule s " +
           "WHERE s.id = :scheduleId " +
           "ORDER BY sa.seatClass ASC")
    List<SeatAvailability> findAllByScheduleIdWithDetails(@Param("scheduleId") Long scheduleId);

    /**
     * Atomically book seats — decrements available, increments booked only if
     * sufficient seats exist. Returns 1 on success, 0 on insufficient seats.
     *
     * The single UPDATE with WHERE-clause guard guarantees NO OVERSELLING even
     * under high concurrent booking load (database row-lock + condition check).
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SeatAvailability sa
           SET sa.availableSeats = sa.availableSeats - :seats,
               sa.bookedSeats    = sa.bookedSeats    + :seats
         WHERE sa.id = :seatAvailabilityId
           AND sa.availableSeats >= :seats
           AND sa.totalSeats     >= :seats
    """)
    int bookSeatsAtomically(
            @Param("seatAvailabilityId") Long seatAvailabilityId,
            @Param("seats") int seats);

    /**
     * Atomically release seats (cancellation flow) — reclaims availability up
     * to the total capacity, never exceeds it.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SeatAvailability sa
           SET sa.availableSeats = LEAST(sa.totalSeats, sa.availableSeats + :seats),
               sa.bookedSeats    = GREATEST(0,            sa.bookedSeats    - :seats)
         WHERE sa.id = :seatAvailabilityId
           AND sa.bookedSeats >= :seats
    """)
    int releaseSeatsAtomically(
            @Param("seatAvailabilityId") Long seatAvailabilityId,
            @Param("seats") int seats);

    /** Direct admin override of the whole availability record (fares + counts). */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE SeatAvailability sa
           SET sa.totalSeats     = :total,
               sa.availableSeats = :available,
               sa.bookedSeats    = :booked,
               sa.fare           = :fare
         WHERE sa.id = :seatAvailabilityId
           AND :available >= 0
           AND :booked    >= 0
           AND :total     >= 0
    """)
    int updateInventoryAndFareAtomically(
            @Param("seatAvailabilityId") Long seatAvailabilityId,
            @Param("total") int total,
            @Param("available") int available,
            @Param("booked") int booked,
            @Param("fare") BigDecimal fare);
}
