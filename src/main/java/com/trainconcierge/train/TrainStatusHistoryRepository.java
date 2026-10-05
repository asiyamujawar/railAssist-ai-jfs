package com.trainconcierge.train;

import com.trainconcierge.schedule.TrainSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TrainStatusHistoryRepository extends JpaRepository<TrainStatusHistory, Long> {

    List<TrainStatusHistory> findByScheduleOrderByRecordedAtDesc(TrainSchedule schedule);

    List<TrainStatusHistory> findByScheduleAndStatus(TrainSchedule schedule, TrainStatus status);

    List<TrainStatusHistory> findByScheduleIn(Collection<TrainSchedule> schedules);
}
