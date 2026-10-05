package com.trainconcierge.admin.simulation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.admin.simulation.dto.*;
import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabModificationAuditRepository;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatClass;
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
import com.trainconcierge.coordination.TravelCoordinationRecordRepository;
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
 * Integration tests for Phase 19: Admin Simulation Control API.
 *
 * <p><strong>SIMULATED SERVICE</strong> — no real railway data provider is
 * contacted in any of these tests.</p>
 *
 * <h2>Coverage</h2>
 * <ol>
 *   <li>Unauthenticated requests → 401 on all admin endpoints</li>
 *   <li>Non-admin (ROLE_USER) requests → 403 on all admin endpoints</li>
 *   <li>Admin trigger delay → 200, status persisted, delay minutes correct</li>
 *   <li>Admin trigger delay — missing scheduleId → 400</li>
 *   <li>Admin trigger delay — delayMinutes = 0 → 400</li>
 *   <li>Admin trigger delay — invalid scheduleId → 404</li>
 *   <li>Admin trigger cancellation → 200, schedule marked cancelled</li>
 *   <li>Admin restore normal → 200, status ON_TIME, delay cleared</li>
 *   <li>Admin trigger monitoring cycle → 200, returns cycle stats</li>
 *   <li>Admin clear monitoring cache → 200, cache cleared confirmation</li>
 *   <li>Admin trigger disruption evaluation → 200, disruption event created</li>
 *   <li>Admin trigger disruption evaluation twice → second skipped (duplicate guard)</li>
 *   <li>Admin trigger disruption evaluation — terminal journey → skipped</li>
 *   <li>Admin trigger disruption evaluation — no confirmed bookings → skipped</li>
 *   <li>Admin trigger disruption evaluation — ON_TIME status → no event created</li>
 *   <li>Admin trigger disruption evaluation — unknown journeyId → 404</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdminSimulationControlIntegrationTest {

    // ── Base URL ───────────────────────────────────────────────────────────
    private static final String ADMIN_CTRL = "/api/admin/simulation/control";

    // ── Test credentials ───────────────────────────────────────────────────
    private static final String ADMIN_EMAIL = "admin.ctrl19@example.com";
    private static final String USER_EMAIL  = "user.ctrl19@example.com";
    private static final String TEST_PASS   = "Phase19@!";

    // ── Shared state ───────────────────────────────────────────────────────
    private String adminToken;
    private String userToken;
    private Long   scheduleId;
    private Long   journeyId;

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
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private HotelBookingRepository hotelBookingRepository;
    @Autowired private HotelModificationAuditRepository hotelAuditRepository;
    @Autowired private CabBookingRepository cabBookingRepository;
    @Autowired private CabModificationAuditRepository cabAuditRepository;
    @Autowired private RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private TravelCoordinationRecordRepository coordinationRepository;

    @Value("${jwt.secret}")        private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        cleanDatabase();

        User admin = createUser(ADMIN_EMAIL, UserRole.ROLE_ADMIN);
        User passenger = createUser(USER_EMAIL, UserRole.ROLE_USER);
        adminToken = buildToken(admin);
        userToken  = buildToken(passenger);

        Train train = trainRepository.save(Train.builder()
                .trainNumber("CTRL001")
                .trainName("Admin Control Express")
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .totalSeats(200)
                .active(true)
                .build());

        TrainSchedule schedule = scheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(LocalDate.now().plusDays(5))
                .scheduledDeparture(LocalTime.of(8, 0))
                .scheduledArrival(LocalTime.of(20, 0))
                .platform("3")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(1200.00))
                .build());
        scheduleId = schedule.getId();

        SeatAvailability seats = seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(SeatClass.SLEEPER)
                .availableSeats(100)
                .totalSeats(100)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(1200.00))
                .build());

        Journey journey = journeyRepository.save(Journey.builder()
                .user(passenger)
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .travelDate(LocalDate.now().plusDays(5))
                .status(JourneyStatus.PLANNED)
                .build());
        journeyId = journey.getId();

        // Create a confirmed booking linking the journey to the schedule
        bookingRepository.save(Booking.builder()
                .user(passenger)
                .schedule(schedule)
                .journey(journey)
                .bookingReference("TC-CTRL19-001")
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SLEEPER)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(1200.00))
                .baseFare(BigDecimal.valueOf(1200.00))
                .currency("INR")
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        coordinationRepository.deleteAll();
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

    private User createUser(String email, UserRole role) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(TEST_PASS))
                .phoneNumber("+91-80000-11111")
                .role(role)
                .enabled(true)
                .build());
    }

    private String buildToken(User user) {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        SecretKey key = Keys.hmacShaKeyFor(keyBytes);
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("uid", user.getId())
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    private TriggerDelayRequest delayReq(int minutes) {
        return TriggerDelayRequest.builder()
                .scheduleId(scheduleId)
                .delayMinutes(minutes)
                .reason("Engine issue at origin station.")
                .build();
    }

    private TriggerCancellationRequest cancelReq() {
        return TriggerCancellationRequest.builder()
                .scheduleId(scheduleId)
                .reason("Strike action — services suspended.")
                .build();
    }

    private RestoreNormalStatusRequest restoreReq() {
        return RestoreNormalStatusRequest.builder()
                .scheduleId(scheduleId)
                .reason("All clear — services resumed.")
                .build();
    }

    private TriggerDisruptionEvaluationRequest evalReq() {
        return TriggerDisruptionEvaluationRequest.builder()
                .journeyId(journeyId)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RBAC — Unauthenticated → 401
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void triggerDelay_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(30))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void triggerCancellation_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-cancellation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    void monitoringCycle_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/monitoring-cycle"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void disruptionEvaluation_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RBAC — Non-admin → 403
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void triggerDelay_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(30))))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void triggerCancellation_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-cancellation")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void monitoringCycle_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/monitoring-cycle")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void disruptionEvaluation_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(9)
    void clearMonitoringCache_NonAdminUser_Returns403() throws Exception {
        mockMvc.perform(delete(ADMIN_CTRL + "/monitoring-cache")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trigger Delay — success & validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    void triggerDelay_Admin_Returns200_WithDelayPersisted() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(45))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("DELAYED"))
                .andExpect(jsonPath("$.data.delayMinutes").value(45))
                .andExpect(jsonPath("$.data.hasHistory").value(true))
                .andExpect(jsonPath("$.message").value(containsString("SIMULATED")));
    }

    @Test
    @Order(11)
    void triggerDelay_Admin_MissingScheduleId_Returns400() throws Exception {
        TriggerDelayRequest req = TriggerDelayRequest.builder()
                .delayMinutes(30)
                .reason("test")
                .build(); // no scheduleId

        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(12)
    void triggerDelay_Admin_ZeroDelayMinutes_Returns400() throws Exception {
        TriggerDelayRequest req = TriggerDelayRequest.builder()
                .scheduleId(scheduleId)
                .delayMinutes(0)
                .build();

        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(13)
    void triggerDelay_Admin_InvalidScheduleId_Returns404() throws Exception {
        TriggerDelayRequest req = TriggerDelayRequest.builder()
                .scheduleId(99999999L)
                .delayMinutes(30)
                .build();

        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Trigger Cancellation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(14)
    void triggerCancellation_Admin_Returns200_ScheduleMarkedCancelled() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-cancellation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value(containsString("SIMULATED")));

        // Verify DB: TrainSchedule is now cancelled
        TrainSchedule updated = scheduleRepository.findById(scheduleId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(updated.isCancelled(),
                "TrainSchedule should be marked cancelled in the database");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Restore Normal Status
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(15)
    void restoreNormal_AfterDelay_Returns200_StatusOnTime() throws Exception {
        // First apply a delay
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(60))))
                .andExpect(status().isOk());

        // Now restore
        mockMvc.perform(post(ADMIN_CTRL + "/restore-normal")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(restoreReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ON_TIME"))
                .andExpect(jsonPath("$.data.delayMinutes").value(0))
                .andExpect(jsonPath("$.message").value(containsString("SIMULATED")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Monitoring Cycle Trigger
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(16)
    void triggerMonitoringCycle_Admin_Returns200_WithCycleStats() throws Exception {
        mockMvc.perform(post(ADMIN_CTRL + "/monitoring-cycle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cycleStartedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cycleCompletedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.journeysEvaluated").isNumber())
                .andExpect(jsonPath("$.data.schedulesPolled").isNumber())
                .andExpect(jsonPath("$.data.changesDetected").isNumber())
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.message").value(containsString("SIMULATED")));
    }

    @Test
    @Order(17)
    void triggerMonitoringCycle_Twice_NoStatusChange_DetectsZeroChanges() throws Exception {
        // First call — seeds the cache as "first observation"
        mockMvc.perform(post(ADMIN_CTRL + "/monitoring-cycle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Second call immediately after — no status change, so changesDetected must be 0
        mockMvc.perform(post(ADMIN_CTRL + "/monitoring-cycle")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changesDetected").value(0));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear Monitoring Cache
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(18)
    void clearMonitoringCache_Admin_Returns200_WithConfirmation() throws Exception {
        mockMvc.perform(delete(ADMIN_CTRL + "/monitoring-cache")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("cleared")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Disruption Evaluation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(19)
    void disruptionEvaluation_OnTimeSchedule_ReturnsSkippedWithNoEvent() throws Exception {
        // Schedule is ON_TIME by default — no disruption should be created
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.journeyId").value(journeyId.intValue()))
                .andExpect(jsonPath("$.data.skipped").value(false))
                .andExpect(jsonPath("$.data.disruptionEventId").doesNotExist());
    }

    @Test
    @Order(20)
    void disruptionEvaluation_AfterDelay_CreatesDisruptionEvent() throws Exception {
        // Apply a significant delay first (above the threshold configured in properties)
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(90))))
                .andExpect(status().isOk());

        // Now trigger disruption evaluation for the journey
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.journeyId").value(journeyId.intValue()))
                .andExpect(jsonPath("$.data.scheduleId").value(scheduleId.intValue()))
                .andExpect(jsonPath("$.data.skipped").value(false))
                .andExpect(jsonPath("$.data.disruptionEventId").isNumber())
                .andExpect(jsonPath("$.data.disruptionType").value("DELAY"))
                .andExpect(jsonPath("$.data.summary").value(containsString("DisruptionEvent")));
    }

    @Test
    @Order(21)
    void disruptionEvaluation_CalledTwice_SecondCallSkippedByDuplicateGuard() throws Exception {
        // Apply delay
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(90))))
                .andExpect(status().isOk());

        // First evaluation — creates disruption event
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipped").value(false));

        // Second evaluation — MUST be skipped (duplicate guard)
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipped").value(true))
                .andExpect(jsonPath("$.data.skipReason").value(containsString("open disruption")));

        // Confirm only ONE disruption event was created
        org.junit.jupiter.api.Assertions.assertEquals(1,
                disruptionEventRepository.count(),
                "Duplicate guard must ensure only ONE disruption event is created.");
    }

    @Test
    @Order(22)
    void disruptionEvaluation_CancelledJourney_IsSkipped() throws Exception {
        // Cancel the journey
        Journey journey = journeyRepository.findById(journeyId).orElseThrow();
        journey.setStatus(JourneyStatus.CANCELLED);
        journeyRepository.save(journey);

        // Apply a delay
        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(60))))
                .andExpect(status().isOk());

        // Evaluation should be skipped because journey is CANCELLED
        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipped").value(true))
                .andExpect(jsonPath("$.data.skipReason").value(containsString("terminal status")));
    }

    @Test
    @Order(23)
    void disruptionEvaluation_UnknownJourneyId_Returns404() throws Exception {
        TriggerDisruptionEvaluationRequest req = TriggerDisruptionEvaluationRequest.builder()
                .journeyId(99999999L)
                .build();

        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(24)
    void disruptionEvaluation_NoConfirmedBookings_IsSkipped() throws Exception {
        // Cancel the booking so there are no confirmed bookings
        Booking booking = bookingRepository.findByJourney(
                journeyRepository.findById(journeyId).orElseThrow()).get(0);
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        mockMvc.perform(post(ADMIN_CTRL + "/trigger-delay")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(delayReq(60))))
                .andExpect(status().isOk());

        mockMvc.perform(post(ADMIN_CTRL + "/disruption-evaluation")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(evalReq())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skipped").value(true))
                .andExpect(jsonPath("$.data.skipReason").value(containsString("no confirmed bookings")));
    }
}
