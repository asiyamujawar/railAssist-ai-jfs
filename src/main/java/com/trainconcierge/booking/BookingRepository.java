package com.trainconcierge.booking;

import com.trainconcierge.journey.Journey;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    boolean existsByBookingReference(String bookingReference);

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.user u " +
           "JOIN FETCH b.schedule s " +
           "JOIN FETCH s.train t " +
           "LEFT JOIN FETCH b.journey j " +
           "WHERE b.id = :id")
    Optional<Booking> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT b FROM Booking b " +
           "JOIN FETCH b.schedule s " +
           "JOIN FETCH s.train t " +
           "LEFT JOIN FETCH b.journey j " +
           "WHERE b.user = :user")
    List<Booking> findAllByUserWithDetails(@Param("user") User user);

    @Query(value = "SELECT b FROM Booking b " +
                   "JOIN FETCH b.schedule s " +
                   "JOIN FETCH s.train t " +
                   "LEFT JOIN FETCH b.journey j " +
                   "WHERE b.user = :user",
           countQuery = "SELECT COUNT(b) FROM Booking b WHERE b.user = :user")
    Page<Booking> findPageByUserWithDetails(@Param("user") User user, Pageable pageable);

    List<Booking> findByUser(User user);

    List<Booking> findByUserAndStatus(User user, BookingStatus status);

    List<Booking> findBySchedule(TrainSchedule schedule);

    List<Booking> findByScheduleAndStatus(TrainSchedule schedule, BookingStatus status);

    List<Booking> findByJourney(Journey journey);

    @Query("SELECT COUNT(b) > 0 FROM Booking b " +
           "WHERE b.user = :user " +
           "  AND b.schedule = :schedule " +
           "  AND b.seatClass = :seatClass " +
           "  AND b.status IN (com.trainconcierge.booking.BookingStatus.PENDING," +
           "                   com.trainconcierge.booking.BookingStatus.CONFIRMED," +
           "                   com.trainconcierge.booking.BookingStatus.COMPLETED)")
    boolean existsActiveDuplicate(@Param("user") User user,
                                  @Param("schedule") TrainSchedule schedule,
                                  @Param("seatClass") com.trainconcierge.seat.SeatClass seatClass);
}
