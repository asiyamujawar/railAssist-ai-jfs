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
public class AuthResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private Instant expiresAt;

    private Long userId;

    private String firstName;

    private String lastName;

    private String email;

    private UserRole role;
}
