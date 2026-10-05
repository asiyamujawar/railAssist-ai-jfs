package com.trainconcierge.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.notification.dto.NotificationResponse;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import com.trainconcierge.user.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for Phase 17 — Notification System.
 *
 * <p>Tests creation, paginated retrieval, unread count, single mark-as-read,
 * bulk read-all, duplicate suppression, and strict cross-user ownership isolation.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    private User user1;
    private User user2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .firstName("Notification")
                .lastName("User1")
                .email("notif.user1@test.com")
                .passwordHash(passwordEncoder.encode("NotifPass123!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("Notification")
                .lastName("User2")
                .email("notif.user2@test.com")
                .passwordHash(passwordEncoder.encode("NotifPass123!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        token1 = createToken(user1.getEmail(), user1.getRole(), user1.getId());
        token2 = createToken(user2.getEmail(), user2.getRole(), user2.getId());
    }

    private String createToken(String email, UserRole role, Long userId) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(email.toLowerCase())
                .claim("role", role.name())
                .claim("uid", userId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    @Order(1)
    void getMyNotifications_ReturnsPaginatedListAndUnreadCount() throws Exception {
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");
        notificationService.notifyDisruptionAlert(user1, 201L, "Express 1", "2026-10-05", "Signals failure");

        mockMvc.perform(get("/api/notifications/my")
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.notifications", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements", is(2)))
                .andExpect(jsonPath("$.data.unreadCount", is(2)))
                .andExpect(jsonPath("$.data.notifications[0].type", is("DISRUPTION_ALERT")));
    }

    @Test
    @Order(2)
    void getUnreadCount_ReturnsCorrectBadgeCount() throws Exception {
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");
        notificationService.notifyHotelConfirmed(user1, 301L, "Grand Plaza", "2026-10-05", "2026-10-07", 2);

        mockMvc.perform(get("/api/notifications/unread-count")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.unreadCount", is(2)));
    }

    @Test
    @Order(3)
    void markAsRead_Success_UpdatesReadFlagAndTimestamp() throws Exception {
        notificationService.notifyCabConfirmed(user1, 401L, "Station", "Hotel", "18:30");
        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(user1);
        Notification notif = list.get(0);

        mockMvc.perform(patch("/api/notifications/{id}/read", notif.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.read", is(true)))
                .andExpect(jsonPath("$.data.readAt", notNullValue()));

        Notification updated = notificationRepository.findById(notif.getId()).orElseThrow();
        assertTrue(updated.isRead());
        assertNotNull(updated.getReadAt());
    }

    @Test
    @Order(4)
    void markAsRead_Forbidden_WhenNotOwner() throws Exception {
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");
        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(user1);
        Notification notif1 = list.get(0);

        // User2 attempts to mark User1's notification as read -> 403 Forbidden
        mockMvc.perform(patch("/api/notifications/{id}/read", notif1.getId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("ACCESS_DENIED")))
                .andExpect(jsonPath("$.message", containsString("Access denied")));
    }

    @Test
    @Order(5)
    void markAllAsRead_Success_UpdatesAllUnreadForUser() throws Exception {
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");
        notificationService.notifyDisruptionAlert(user1, 201L, "Express 1", "2026-10-05", "Track maintenance");
        notificationService.notifyCabConfirmed(user1, 401L, "Station", "Hotel", "18:30");

        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.updatedCount", is(3)));

        long unread = notificationRepository.countByUserAndReadFalse(user1);
        assertEquals(0, unread);
    }

    @Test
    @Order(6)
    void duplicateSuppression_PreventsDuplicateNotifications() {
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");
        // Second call with same user, type, refId
        notificationService.notifyBookingConfirmed(user1, 101L, "TC-REF-101", "Express 1", "2026-10-05");

        List<Notification> list = notificationRepository.findByUserOrderByCreatedAtDesc(user1);
        assertEquals(1, list.size());
    }

    @Test
    @Order(7)
    void notificationCreation_SupportsAllTypes() {
        notificationService.notifyBookingConfirmed(user1, 1L, "REF1", "T1", "2026-10-01");
        notificationService.notifyBookingCancelled(user1, 2L, "REF2", "T1");
        notificationService.notifyDisruptionAlert(user1, 3L, "T1", "2026-10-01", "Delay");
        notificationService.notifyRebookingSuggestion(user1, 3L, 2);
        notificationService.notifyRebookingConfirmed(user1, 4L, "REF3", "T2", "2026-10-01", "14:00");
        notificationService.notifyHotelConfirmed(user1, 5L, "H1", "2026-10-01", "2026-10-02", 1);
        notificationService.notifyHotelRescheduled(user1, 5L, "H1", "2026-10-01", "2026-10-03");
        notificationService.notifyHotelCancelled(user1, 5L, "H1");
        notificationService.notifyCabConfirmed(user1, 6L, "A", "B", "10:00");
        notificationService.notifyCabRescheduled(user1, 6L, "A", "11:00");
        notificationService.notifyCabCancelled(user1, 6L, "A");

        List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(user1);
        assertEquals(11, all.size());
    }
}
