package com.trainconcierge.auth;

import com.trainconcierge.auth.dto.*;
import com.trainconcierge.exception.DuplicateResourceException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.UnauthorizedException;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("User", "email", request.getEmail());
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("Registered new user with id: {}", savedUser.getId());

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(savedUser.getEmail());
        return buildAuthResponse(userDetails, savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail().toLowerCase(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            User user = customUserDetailsService.loadUserEntityByEmail(userDetails.getUsername());

            log.info("User logged in: {}", user.getId());
            return buildAuthResponse(userDetails, user);

        } catch (BadCredentialsException ex) {
            throw new UnauthorizedException("Invalid email or password.", ErrorCode.AUTH_TOKEN_INVALID);
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Authentication failed. Please try again.", ErrorCode.AUTH_REQUIRED);
        }
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String) {
            throw new UnauthorizedException("Authentication is required to access this resource.");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = customUserDetailsService.loadUserEntityByEmail(userDetails.getUsername());

        return UserProfileResponse.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .preferredNotificationChannel(user.getPreferredNotificationChannel())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public User getCurrentAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() instanceof String) {
            throw new UnauthorizedException("Authentication is required.");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return customUserDetailsService.loadUserEntityByEmail(userDetails.getUsername());
    }

    private AuthResponse buildAuthResponse(UserDetails userDetails, User user) {
        String token = jwtTokenProvider.generateToken(userDetails);
        long expiresIn = jwtTokenProvider.getExpirationMs();
        Instant expiresAt = Instant.now().plusMillis(expiresIn);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(expiresIn / 1000)
                .expiresAt(expiresAt)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .role(user.getRole())
                .build();
    }
}
