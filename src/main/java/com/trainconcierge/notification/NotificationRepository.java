package com.trainconcierge.notification;

import com.trainconcierge.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link Notification} entities.
 *
 * <p>Phase 17 — added pagination, unread count, read-all, and
 * duplicate-prevention queries.</p>
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ── Legacy list queries (used by SeatAlertMonitorService) ─────────────

    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    List<Notification> findByUserAndReadFalseOrderByCreatedAtDesc(User user);

    List<Notification> findBySentFalse();

    List<Notification> findByUserAndType(User user, NotificationType type);

    // ── Paginated retrieval ────────────────────────────────────────────────

    /**
     * Returns a page of notifications for the given user, newest first.
     */
    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    // ── Unread count ───────────────────────────────────────────────────────

    long countByUserAndReadFalse(User user);

    // ── Bulk read-all ──────────────────────────────────────────────────────

    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :now " +
           "WHERE n.user = :user AND n.read = false")
    int markAllReadForUser(@Param("user") User user, @Param("now") java.time.Instant now);

    // ── Duplicate prevention ───────────────────────────────────────────────

    /**
     * Checks whether a notification with the same (user, type, referenceId)
     * tuple already exists, preventing duplicate writes for repeated events.
     */
    boolean existsByUserAndTypeAndReferenceId(User user, NotificationType type, String referenceId);
}
