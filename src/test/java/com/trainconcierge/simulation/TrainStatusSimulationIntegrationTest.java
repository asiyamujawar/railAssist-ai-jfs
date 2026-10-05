package com.trainconcierge.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabModificationAuditRepository;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import com.trainconcierge.train.TrainStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Mock Train Status Simulation module (Phase 10).
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway data provider is
 * contacted in any of these tests. All status transitions are driven by the
 * admin PUT endpoint.</p>
 *
 * Test coverage:
 * <ol>
 *   <li>GET latest status returns default ON_TIME when no history exists</li>
 *   <li>Admin PUT updates status to DELAYED with delay minutes persisted</li>
 *   <li>Admin PUT updates status to CANCELLED</li>
 *   <li>Admin PUT updates status to PLATFORM_CHANGED with platform value</li>
 *   <li>Admin PUT updates status to ON_TIME clears delay</li>
 *   <li>DELAYED without delayMinutes returns 400 Bad Request</li>
 *   <li>PLATFORM_CHANGED without platform returns 400 Bad Request</li>
 *   <li>GET history returns entries newest-first after multiple updates</li>
 *   <li>Non-admin user cannot call admin PUT endpoint (403)</li>
 *   <li>Unauthenticated user cannot call any endpoint (401)</li>
 *   <li>Invalid scheduleId returns 404</li>
 *   <li>DELAYED with delayMinutes = 0 returns 400 Bad Request</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrainStatusSimulationIntegrationTest {

    // ── Base URLs ──────────────────────────────────────────────────────────
    private static final String SIM_BASE  = "/api/simulation/trains";
    private static final String ADMIN_BASE = "/api/admin/simulation/trains";

    // ── Test credentials ───────────────────────────────────────────────────
    private static final String ADMIN_EMAIL = "admin.sim@example.com";
    private static final String USER_EMAIL  = "user.sim@example.com";
    private static final String TEST_PASS   = "Sim@2026!";

    // ── Shared state (set per test via @BeforeEach) ─────────────────────────
    private String adminToken;
    private String userToken;
    private Long   scheduleId;

    // ── Spring beans ───────────────────────────────────────────────────────
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private TrainStatusHistoryRepository historyRepository;
    @Autowired private SeatAvailabilityRepository seatAvailabilityRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private CabBookingRepository cabBookingRepository;
    @Autowired private CabModificationAuditRepository cabAuditRepository;
    @Autowired private HotelBookingRepository hotelBookingRepository;
    @Autowired private HotelModificationAuditRepository hotelAuditRepository;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private com.trainconcierge.rebooking.RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private com.trainconcierge.coordination.TravelCoordinationRecordRepository travelCoordinationRecordRepository;

    @Value("${jwt.secret}")          private String jwtSecret;
    @Value("${jwt.expiration-ms}")   private long jwtExpirationMs;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        cleanDatabase();

        adminToken = createUserAndGetToken(ADMIN_EMAIL, UserRole.ROLE_ADMIN);
        userToken  = createUserAndGetToken(USER_EMAIL,  UserRole.ROLE_USER);

        Train train = trainRepository.save(Train.builder()
                .trainNumber("SIM001")
                .trainName("Simulation Express")
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .totalSeats(200)
                .active(true)
                .build());

        TrainSchedule schedule = scheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(LocalDate.now().plusDays(3))
                .scheduledDeparture(LocalTime.of(8, 0))
                .scheduledArrival(LocalTime.of(20, 0))
                .platform("1")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(1500.00))
                .build());

        scheduleId = schedule.getId();
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        travelCoordinationRecordRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        cabAuditRepository.deleteAll();
        cabBookingRepository.deleteAll();
        hotelAuditRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        historyRepository.deleteAll();
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
                .phoneNumber("+91-80000-12345")
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

    private UpdateSimulatedStatusRequest delayedRequest(int minutes) {
        return UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.DELAYED)
                .delayMinutes(minutes)
                .message("Engine delay at origin.")
                .build();
    }

    private UpdateSimulatedStatusRequest platformChangedRequest(String platform) {
        return UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.PLATFORM_CHANGED)
                .platform(platform)
                .message("Platform reassigned by station authority.")
                .build();
    }

    private UpdateSimulatedStatusRequest basicRequest(TrainStatus status) {
        return UpdateSimulatedStatusRequest.builder()
                .status(status)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases
    // ─────────────────────────────────────────────────────────────────────────

    // ── 1. Default status returns ON_TIME-equivalent with no history ─────────

    @Test
    @Order(1)
    void getLatestStatus_NoHistoryYet_ReturnsScheduledDefaults() throws Exception {
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId.intValue()))
                .andExpect(jsonPath("$.data.trainNumber").value("SIM001"))
                .andExpect(jsonPath("$.data.source").value("MOCK_TRAIN_STATUS_SERVICE"))
                .andExpect(jsonPath("$.data.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.data.hasHistory").value(false))
                .andExpect(jsonPath("$.data.delayMinutes").value(0));
    }

    // ── 2. Admin sets DELAYED — delay minutes and history row persisted ───────

    @Test
    @Order(2)
    void adminUpdate_SetDelayed_PersistsHistoryAndUpdatesSchedule() throws Exception {
        UpdateSimulatedStatusRequest req = delayedRequest(45);

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELAYED"))
                .andExpect(jsonPath("$.data.delayMinutes").value(45))
                .andExpect(jsonPath("$.data.hasHistory").value(true))
                .andExpect(jsonPath("$.data.message").value("Engine delay at origin."));

        // Confirm one history row exists
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("DELAYED"))
                .andExpect(jsonPath("$.data[0].delayMinutes").value(45))
                .andExpect(jsonPath("$.data[0].simulated").value(true));
    }

    // ── 3. Admin sets CANCELLED ──────────────────────────────────────────────

    @Test
    @Order(3)
    void adminUpdate_SetCancelled_UpdatesScheduleStatus() throws Exception {
        UpdateSimulatedStatusRequest req = basicRequest(TrainStatus.CANCELLED);
        req.setMessage("Strike action — all services suspended.");

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.delayMinutes").value(0));

        // Verify the TrainSchedule entity is marked as cancelled
        TrainSchedule updated = scheduleRepository.findById(scheduleId).orElseThrow();
        assert updated.isCancelled();
    }

    // ── 4. Admin sets PLATFORM_CHANGED ───────────────────────────────────────

    @Test
    @Order(4)
    void adminUpdate_SetPlatformChanged_PersistsPlatform() throws Exception {
        UpdateSimulatedStatusRequest req = platformChangedRequest("7A");

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PLATFORM_CHANGED"))
                .andExpect(jsonPath("$.data.platform").value("7A"))
                .andExpect(jsonPath("$.data.delayMinutes").value(0));

        // History should include the platform value
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].platform").value("7A"));
    }

    // ── 5. Admin sets ON_TIME — delay cleared ────────────────────────────────

    @Test
    @Order(5)
    void adminUpdate_SetOnTimeAfterDelay_ClearsDelay() throws Exception {
        // First delay it
        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayedRequest(30))))
                .andExpect(status().isOk());

        // Now set ON_TIME
        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(basicRequest(TrainStatus.ON_TIME))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_TIME"))
                .andExpect(jsonPath("$.data.delayMinutes").value(0));

        // Two history entries now exist
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    // ── 6. DELAYED without delayMinutes → 400 ────────────────────────────────

    @Test
    @Order(6)
    void adminUpdate_Delayed_WithoutDelayMinutes_Returns400() throws Exception {
        UpdateSimulatedStatusRequest req = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.DELAYED)
                .build();

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── 7. DELAYED with delayMinutes = 0 → 400 ───────────────────────────────

    @Test
    @Order(7)
    void adminUpdate_Delayed_ZeroDelayMinutes_Returns400() throws Exception {
        UpdateSimulatedStatusRequest req = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.DELAYED)
                .delayMinutes(0)
                .build();

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── 8. PLATFORM_CHANGED without platform → 400 ───────────────────────────

    @Test
    @Order(8)
    void adminUpdate_PlatformChanged_WithoutPlatform_Returns400() throws Exception {
        UpdateSimulatedStatusRequest req = UpdateSimulatedStatusRequest.builder()
                .status(TrainStatus.PLATFORM_CHANGED)
                .build();

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── 9. History returns entries newest-first ───────────────────────────────

    @Test
    @Order(9)
    void getHistory_MultipleUpdates_ReturnedNewestFirst() throws Exception {
        // Transition: DELAYED → PLATFORM_CHANGED → ON_TIME
        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(delayedRequest(20)))).andExpect(status().isOk());

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(platformChangedRequest("3B")))).andExpect(status().isOk());

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(basicRequest(TrainStatus.ON_TIME)))).andExpect(status().isOk());

        // History: ON_TIME (newest) → PLATFORM_CHANGED → DELAYED (oldest)
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].status").value("ON_TIME"))
                .andExpect(jsonPath("$.data[1].status").value("PLATFORM_CHANGED"))
                .andExpect(jsonPath("$.data[2].status").value("DELAYED"));
    }

    // ── 10. Non-admin user cannot use admin endpoint (403) ────────────────────

    @Test
    @Order(10)
    void adminUpdate_NonAdminUser_Returns403() throws Exception {
        UpdateSimulatedStatusRequest req = delayedRequest(15);

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── 11. Unauthenticated request → 401 ────────────────────────────────────

    @Test
    @Order(11)
    void getLatestStatus_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    void adminUpdate_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayedRequest(10))))
                .andExpect(status().isUnauthorized());
    }

    // ── 12. Invalid scheduleId → 404 ─────────────────────────────────────────

    @Test
    @Order(13)
    void getLatestStatus_InvalidScheduleId_Returns404() throws Exception {
        mockMvc.perform(get(SIM_BASE + "/99999999/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(14)
    void getHistory_InvalidScheduleId_Returns404() throws Exception {
        mockMvc.perform(get(SIM_BASE + "/99999999/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    void adminUpdate_InvalidScheduleId_Returns404() throws Exception {
        mockMvc.perform(put(ADMIN_BASE + "/99999999/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayedRequest(10))))
                .andExpect(status().isNotFound());
    }

    // ── 13. Disclaimer and source marker always present ───────────────────────

    @Test
    @Order(16)
    void getLatestStatus_ResponseAlwaysContainsSimulatedDisclaimer() throws Exception {
        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("MOCK_TRAIN_STATUS_SERVICE"))
                .andExpect(jsonPath("$.data.disclaimer").value(
                        containsString("simulated")));
    }

    // ── 14. Simulated flag is always true in history entries ──────────────────

    @Test
    @Order(17)
    void history_AllEntriesMarkedSimulatedTrue() throws Exception {
        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(delayedRequest(5)))).andExpect(status().isOk());

        mockMvc.perform(get(SIM_BASE + "/" + scheduleId + "/history")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].simulated").value(true));
    }

    // ── 15. Missing status body → 400 ────────────────────────────────────────

    @Test
    @Order(18)
    void adminUpdate_MissingStatusField_Returns400() throws Exception {
        String body = "{\"delayMinutes\": 10}"; // no status field

        mockMvc.perform(put(ADMIN_BASE + "/" + scheduleId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
