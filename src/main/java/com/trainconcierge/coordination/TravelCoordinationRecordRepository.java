package com.trainconcierge.coordination;

import com.trainconcierge.journey.Journey;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TravelCoordinationRecordRepository extends JpaRepository<TravelCoordinationRecord, Long> {

    List<TravelCoordinationRecord> findByRebookingHistory(RebookingHistory history);

    List<TravelCoordinationRecord> findByJourney(Journey journey);

    List<TravelCoordinationRecord> findByUser(User user);

    boolean existsByRebookingHistoryAndOverallStatusIn(
            RebookingHistory history, List<CoordinationOverallStatus> statuses);

    @Query("SELECT tc FROM TravelCoordinationRecord tc " +
           "JOIN FETCH tc.rebookingHistory rh " +
           "JOIN FETCH tc.journey j " +
           "JOIN FETCH tc.user u " +
           "WHERE tc.journey = :journey " +
           "ORDER BY tc.createdAt DESC")
    List<TravelCoordinationRecord> findByJourneyWithDetails(@Param("journey") Journey journey);

    @Query("SELECT tc FROM TravelCoordinationRecord tc " +
           "JOIN FETCH tc.rebookingHistory rh " +
           "JOIN FETCH tc.journey j " +
           "JOIN FETCH tc.user u " +
           "WHERE tc.id = :id")
    Optional<TravelCoordinationRecord> findByIdWithDetails(@Param("id") Long id);
}
