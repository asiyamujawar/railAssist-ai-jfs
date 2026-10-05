package com.trainconcierge.seat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabModificationAuditRepository;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.notification.NotificationRepository;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.dto.CreateSeatAlertRequest;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import com.trainconcierge.train.TrainStatusHistoryRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Phase 11 — Smart Seat Availability Alert module.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>Subscribe successfully — returns 201 with subscription details</li>
 *   <li>Duplicate subscription returns 400</li>
 *   <li>GET /my returns all subscriptions for user</li>
 *   <li>PATCH deactivate — successfully deactivates active alert</li>
 *   <li>PATCH deactivate already-inactive — returns 400</li>
 *   <li>PATCH deactivate another user's alert — returns 403</li>
 *   <li>Monitor fires alert when availableSeats {@literal <=} threshold (threshold=0, seats=0)</li>
 *   <li>Monitor does NOT fire when availableSeats {@literal >} threshold</li>
 *   <li>Monitor does NOT fire twice (duplicate suppression)</li>
 *   <li>Monitor fires when threshold > 0 and seats <= threshold</li>
 *   <li>Unauthenticated access returns 401</li>
 *   <li>Invalid scheduleId returns 404</li>
 *   <li>Negative threshold returns 400</li>
 *   <li>Alert history persisted after monitor fires</li>
 *   <li>Notification record created after monitor fires</li>
 * </ol>
 * </p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SeatAlertIntegrationTest {

    private static final String BASE_URL = "/api/seat-alerts";

    private static final String USER_A_EMAIL = "user.a.alert@example.com";
    private static final String USER_B_EMAIL = "user.b.alert@example.com";
    private static final String TEST_PASS    = "Alert@2026!";

    private String userAToken;
    private String userBToken;

    private TrainSchedule schedule;
    private SeatAvailability seatAvailabilitySecond;  // initially 0 seats
    private SeatAvailability seatAvailabilityFirst;   // initially 10 seats

    // ── Spring beans ───────────────────────────────────────────────────────
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private SeatAvailabilityRepository seatAvailabilityRepository;
    @Autowired private SeatAlertSubscriptionRepository subscriptionRepository;
    @Autowired private SeatAlertHistoryRepository alertHistoryRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private CabBookingRepository cabBookingRepository;
    @Autowired private CabModificationAuditRepository cabAuditRepository;
    @Autowired private HotelBookingRepository hotelBookingRepository;
    @Autowired private HotelModificationAuditRepository hotelAuditRepository;
    @Autowired private TrainStatusHistoryRepository trainStatusHistoryRepository;
    @Autowired private SeatAlertMonitorService monitorService;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private RecommendationRepository recommendationRepository;

    @Value("${jwt.secret}")         private String jwtSecret;
    @Value("${jwt.expiration-ms}")  private long jwtExpirationMs;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userAToken = createUserAndGetToken(USER_A_EMAIL, UserRole.ROLE_USER);
        userBToken = createUserAndGetToken(USER_B_EMAIL, UserRole.ROLE_USER);

        Train train = trainRepository.save(Train.builder()
                .trainNumber("ALT001")
                .trainName("Alert Express")
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .totalSeats(300)
                .active(true)
                .build());

        schedule = scheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(LocalDate.now().plusDays(10))
                .scheduledDeparture(LocalTime.of(7, 0))
                .scheduledArrival(LocalTime.of(19, 0))
                .platform("3")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(999.00))
                .build());

        // SECOND class — 0 available (fully booked)
        seatAvailabilitySecond = seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(0)
                .bookedSeats(100)
                .fare(BigDecimal.valueOf(300.00))
                .build());

        // FIRST class — 10 available
        seatAvailabilityFirst = seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(SeatClass.FIRST)
                .totalSeats(50)
                .availableSeats(10)
                .bookedSeats(40)
                .fare(BigDecimal.valueOf(800.00))
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        alertHistoryRepository.deleteAll();
        subscriptionRepository.deleteAll();
        notificationRepository.deleteAll();
        cabAuditRepository.deleteAll();
        cabBookingRepository.deleteAll();
        hotelAuditRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        trainStatusHistoryRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        scheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(TEST_PASS))
                .phoneNumber("+91-70000-11111")
                .role(role)
                .enabled(true)
                .build();
        User saved = userRepository.save(user);
        return buildToken(saved.getEmail(), role, saved.getId());
    }

    private String buildToken(String email, UserRole role, Long userId) {
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

    private CreateSeatAlertRequest alertRequest(long schedId, SeatClass cls, int threshold) {
        return CreateSeatAlertRequest.builder()
                .scheduleId(schedId)
                .seatClass(cls)
                .threshold(threshold)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void subscribe_Success_Returns201() throws Exception {
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.seatClass").value("SECOND"))
                .andExpect(jsonPath("$.data.threshold").value(0))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.triggered").value(false))
                .andExpect(jsonPath("$.data.alertCount").value(0))
                .andExpect(jsonPath("$.data.scheduleId").value(schedule.getId().intValue()))
                .andExpect(jsonPath("$.data.trainNumber").value("ALT001"));
    }

    @Test
    @Order(2)
    void subscribe_Duplicate_Returns400() throws Exception {
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        // First subscription
        mockMvc.perform(post(BASE_URL)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Duplicate
        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    void getMyAlerts_ReturnsAllSubscriptionsForUser() throws Exception {
        // Create 2 subscriptions for user A (different classes)
        mockMvc.perform(post(BASE_URL)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        alertRequest(schedule.getId(), SeatClass.SECOND, 0))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        alertRequest(schedule.getId(), SeatClass.FIRST, 5))))
                .andExpect(status().isCreated());

        // User A sees 2
        mockMvc.perform(get(BASE_URL + "/my")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        // User B sees 0
        mockMvc.perform(get(BASE_URL + "/my")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Order(4)
    void deactivate_Success_Returns200() throws Exception {
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        String createBody = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long alertId = objectMapper.readTree(createBody).get("data").get("id").asLong();

        mockMvc.perform(patch(BASE_URL + "/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @Order(5)
    void deactivate_AlreadyInactive_Returns400() throws Exception {
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        String createBody = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long alertId = objectMapper.readTree(createBody).get("data").get("id").asLong();

        // First deactivate
        mockMvc.perform(patch(BASE_URL + "/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        // Second deactivate → 400
        mockMvc.perform(patch(BASE_URL + "/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void deactivate_OtherUsersAlert_Returns403() throws Exception {
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        String createBody = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long alertId = objectMapper.readTree(createBody).get("data").get("id").asLong();

        // User B tries to deactivate user A's alert
        mockMvc.perform(patch(BASE_URL + "/" + alertId + "/deactivate")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get(BASE_URL + "/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void subscribe_InvalidScheduleId_Returns404() throws Exception {
        CreateSeatAlertRequest req = alertRequest(99999999L, SeatClass.SECOND, 0);

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(9)
    void subscribe_NegativeThreshold_Returns400() throws Exception {
        String body = """
                {
                  "scheduleId": %d,
                  "seatClass": "SECOND",
                  "threshold": -1
                }
                """.formatted(schedule.getId());

        mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduler / monitor behaviour tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void monitor_FiresAlert_WhenSeatsAtOrBelowThreshold_Default0() {
        // SECOND class has 0 available — threshold=0 → condition met (0 <= 0)
        User userA = userRepository.findByEmail(USER_A_EMAIL).orElseThrow();

        SeatAlertSubscription sub = subscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.SECOND)
                .threshold(0)
                .active(true)
                .triggered(false)
                .build());

        boolean fired = monitorService.checkAndFire(sub);

        assertTrue(fired, "Alert should fire when availableSeats <= threshold");
        SeatAlertSubscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertTrue(updated.isTriggered());
        assertFalse(updated.isActive());
        assertEquals(1, updated.getAlertCount());
        assertNotNull(updated.getTriggeredAt());
        assertNotNull(updated.getLastAlertedAt());

        // History entry persisted
        assertEquals(1, alertHistoryRepository.findByUserOrderByAlertedAtDesc(userA).size());

        // Notification created
        assertEquals(1, notificationRepository.findByUserOrderByCreatedAtDesc(userA).size());
    }

    @Test
    @Order(11)
    void monitor_DoesNotFireAlert_WhenSeatsAboveThreshold() {
        // FIRST class has 10 available — threshold=5 → condition NOT met (10 > 5)
        User userA = userRepository.findByEmail(USER_A_EMAIL).orElseThrow();

        SeatAlertSubscription sub = subscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.FIRST)
                .threshold(5)
                .active(true)
                .triggered(false)
                .build());

        boolean fired = monitorService.checkAndFire(sub);

        assertFalse(fired, "Alert should NOT fire when availableSeats > threshold");
        SeatAlertSubscription unchanged = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertFalse(unchanged.isTriggered());
        assertTrue(unchanged.isActive());
        assertEquals(0, unchanged.getAlertCount());

        assertEquals(0, alertHistoryRepository.findByUserOrderByAlertedAtDesc(userA).size());
        assertEquals(0, notificationRepository.findByUserOrderByCreatedAtDesc(userA).size());
    }

    @Test
    @Order(12)
    void monitor_NoDuplicateAlert_WhenAlreadyTriggered() {
        // Trigger once
        User userA = userRepository.findByEmail(USER_A_EMAIL).orElseThrow();

        SeatAlertSubscription sub = subscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.SECOND)
                .threshold(0)
                .active(true)
                .triggered(false)
                .build());

        assertTrue(monitorService.checkAndFire(sub));

        // Reload from DB — now triggered=true, active=false
        SeatAlertSubscription afterFirst = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertEquals(1, afterFirst.getAlertCount());

        // Re-run monitor — should not fire again (triggered=true excluded from scheduler query)
        // We simulate by calling checkAndFire directly; but since triggered=true we reset it
        // to show that even if somehow called again, no duplicate history row should be added
        // because the scheduler query excludes triggered=true subscriptions.

        // Verify the scheduler query returns 0 subscriptions
        assertEquals(0, subscriptionRepository.findAllActiveUntriggered().size());

        // History should still be 1
        assertEquals(1, alertHistoryRepository.findByUserOrderByAlertedAtDesc(userA).size());
    }

    @Test
    @Order(13)
    void monitor_FiresAlert_WhenThresholdGreaterThanZeroAndSeatsAtOrBelow() {
        // FIRST class has 10 available — threshold=10 → condition met (10 <= 10)
        User userA = userRepository.findByEmail(USER_A_EMAIL).orElseThrow();

        SeatAlertSubscription sub = subscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.FIRST)
                .threshold(10)
                .active(true)
                .triggered(false)
                .build());

        boolean fired = monitorService.checkAndFire(sub);
        assertTrue(fired, "Alert should fire when availableSeats equals threshold");

        SeatAlertSubscription updated = subscriptionRepository.findById(sub.getId()).orElseThrow();
        assertTrue(updated.isTriggered());
        assertEquals(1, updated.getAlertCount());
    }

    @Test
    @Order(14)
    void monitor_AlertHistory_ContainsCorrectDetails() {
        User userA = userRepository.findByEmail(USER_A_EMAIL).orElseThrow();

        SeatAlertSubscription sub = subscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.SECOND)
                .threshold(0)
                .active(true)
                .triggered(false)
                .build());

        monitorService.checkAndFire(sub);

        SeatAlertHistory history = alertHistoryRepository
                .findByUserOrderByAlertedAtDesc(userA).get(0);

        assertEquals(0, history.getAvailableSeatsAtAlert());
        assertEquals(0, history.getThreshold());
        assertNotNull(history.getAlertedAt());
        assertNotNull(history.getMessage());
        assertTrue(history.getMessage().contains("SECOND"));
    }

    @Test
    @Order(15)
    void fullCycle_Subscribe_AndMonitorFires() throws Exception {
        // Subscribe via API
        CreateSeatAlertRequest req = alertRequest(schedule.getId(), SeatClass.SECOND, 0);

        String createBody = mockMvc.perform(post(BASE_URL)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long alertId = objectMapper.readTree(createBody).get("data").get("id").asLong();

        // Manually trigger monitor cycle
        monitorService.runMonitoringCycle();

        // Check via API that subscription is now triggered/inactive
        mockMvc.perform(get(BASE_URL + "/my")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].triggered").value(true))
                .andExpect(jsonPath("$.data[0].active").value(false))
                .andExpect(jsonPath("$.data[0].alertCount").value(1));
    }
}
