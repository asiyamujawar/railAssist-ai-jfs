package com.trainconcierge.schedule;

import com.trainconcierge.train.Train;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainScheduleRepository extends JpaRepository<TrainSchedule, Long> {

    Optional<TrainSchedule> findByTrainAndScheduledDate(Train train, LocalDate scheduledDate);

    boolean existsByTrainAndScheduledDate(Train train, LocalDate scheduledDate);

    List<TrainSchedule> findByScheduledDate(LocalDate scheduledDate);

    List<TrainSchedule> findByTrainAndScheduledDateBetween(
            Train train, LocalDate from, LocalDate to);

    List<TrainSchedule> findByCancelledFalseAndScheduledDate(LocalDate date);

    @Query("""
        SELECT s FROM TrainSchedule s
        JOIN FETCH s.train t
        WHERE s.id = :id
    """)
    Optional<TrainSchedule> findByIdWithTrain(@Param("id") Long id);

    @Query("""
        SELECT s FROM TrainSchedule s
        JOIN s.train t
        WHERE t.active = true
    """)
    Page<TrainSchedule> findAllActiveSchedules(Pageable pageable);

    @Query("""
        SELECT s FROM TrainSchedule s
        WHERE s.train.originStation = :origin
          AND s.train.destinationStation = :destination
          AND s.scheduledDate = :date
          AND s.cancelled = false
        ORDER BY s.scheduledDeparture
    """)
    List<TrainSchedule> findAvailableSchedules(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("date") LocalDate date);

    @Query("""
        SELECT s FROM TrainSchedule s
        JOIN FETCH s.train t
        WHERE t.active = true
          AND LOWER(t.originStation) = LOWER(:origin)
          AND LOWER(t.destinationStation) = LOWER(:destination)
          AND s.scheduledDate = :date
          AND s.cancelled = false
        ORDER BY s.scheduledDeparture ASC
    """)
    List<TrainSchedule> findAvailableSchedulesIgnoreCase(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("date") LocalDate date);
}
