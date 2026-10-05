package com.trainconcierge.seat;

import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeatAlertHistoryRepository extends JpaRepository<SeatAlertHistory, Long> {

    List<SeatAlertHistory> findByUserOrderByAlertedAtDesc(User user);

    List<SeatAlertHistory> findBySubscriptionOrderByAlertedAtDesc(SeatAlertSubscription subscription);

    @Query("""
            SELECT h FROM SeatAlertHistory h
            JOIN FETCH h.subscription s
            JOIN FETCH s.schedule sc
            JOIN FETCH sc.train
            WHERE h.user = :user
            ORDER BY h.alertedAt DESC
            """)
    List<SeatAlertHistory> findByUserWithDetails(@Param("user") User user);
}
