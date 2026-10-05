package com.trainconcierge.recommendation;

import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    List<Recommendation> findByDisruptionEventAndUser(DisruptionEvent event, User user);

    List<Recommendation> findByDisruptionEventInAndUser(Collection<DisruptionEvent> events, User user);

    List<Recommendation> findByUser(User user);

    List<Recommendation> findByUserAndStatus(User user, String status);

    List<Recommendation> findByDisruptionEventOrderByScoreDesc(DisruptionEvent event);
}
