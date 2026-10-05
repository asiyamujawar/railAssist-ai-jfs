package com.trainconcierge.auth.dto;

import com.trainconcierge.user.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;

    private String firstName;

    private String lastName;

    private String email;

    private String phoneNumber;

    private UserRole role;

    private boolean enabled;

    private String preferredNotificationChannel;

    private Instant createdAt;

    private Instant updatedAt;
}
