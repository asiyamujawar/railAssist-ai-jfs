package com.trainconcierge.notification;

import com.trainconcierge.common.BaseEntity;
import com.trainconcierge.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records every notification sent or queued for a User.
 *
 * Acts as both the outbox (pending delivery) and the inbox
 * (delivered, read history visible in-app).
 *
 * Relationships:
 * - Many Notifications → One User
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    /**
     * Optional deep-link reference — e.g. booking ID, disruption ID —
     * stored as a string to keep this table generic and decoupled.
     */
    @Column(length = 100)
    private String referenceId;

    @Column(length = 50)
    private String referenceType;

    @Column(nullable = false)
    @Builder.Default
    private boolean sent = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    private Instant sentAt;
    private Instant readAt;
}
