package com.trainconcierge.disruption;

import com.trainconcierge.journey.Journey;
import com.trainconcierge.schedule.TrainSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

    List<DisruptionEvent> findBySchedule(TrainSchedule schedule);

    List<DisruptionEvent> findByScheduleIn(Collection<TrainSchedule> schedules);

    List<DisruptionEvent> findByJourney(Journey journey);

    List<DisruptionEvent> findByJourneyUserIdOrderByDetectedAtDesc(Long userId);

    Optional<DisruptionEvent> findByIdAndJourneyUserId(Long id, Long userId);

    List<DisruptionEvent> findByResolvedFalse();

    List<DisruptionEvent> findByScheduleAndResolvedFalse(TrainSchedule schedule);

    List<DisruptionEvent> findByScheduleAndJourneyAndResolvedFalse(TrainSchedule schedule, Journey journey);

    List<DisruptionEvent> findByScheduleAndStatusNotIn(TrainSchedule schedule, Collection<DisruptionStatus> statuses);

    List<DisruptionEvent> findByScheduleAndJourneyAndStatusNotIn(TrainSchedule schedule, Journey journey, Collection<DisruptionStatus> statuses);

    List<DisruptionEvent> findBySeverity(DisruptionSeverity severity);

    List<DisruptionEvent> findByType(DisruptionType type);
}
