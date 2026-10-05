package com.trainconcierge.user;

import com.trainconcierge.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a registered passenger or admin user.
 *
 * Relationships:
 * - One User → many Bookings
 * - One User → many Journeys
 * - One User → many Notifications
 * - One User → many SeatAlertSubscriptions
 * - One User → many RebookingHistory records
 *
 * Note: password field stores BCrypt hash — never plaintext.
 * Collections are mapped on the owning side of each relationship
 * and are intentionally NOT mapped here to avoid lazy-load issues
 * and circular serialisation. Traverse via repositories.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_email", columnNames = "email")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt-hashed password — never store or return plaintext. */
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private UserRole role = UserRole.ROLE_USER;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /** Preferred notification channel for disruption alerts. */
    @Column(length = 20)
    private String preferredNotificationChannel;
}
