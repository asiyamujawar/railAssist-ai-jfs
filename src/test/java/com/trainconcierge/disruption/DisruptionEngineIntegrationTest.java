package com.trainconcierge.disruption;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.monitoring.DisruptionDetectionService;
import com.trainconcierge.monitoring.MonitoredStatusChange;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import com.trainconcierge.train.TrainStatus;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Disruption Detection Engine and REST APIs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DisruptionEngineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private DisruptionDetectionService detectionService;
    @Autowired private DisruptionQueryService queryService;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private com.trainconcierge.recommendation.RecommendationRepository recommendationRepository;
    @Autowired private com.trainconcierge.rebooking.RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private com.trainconcierge.coordination.TravelCoordinationRecordRepository travelCoordinationRecordRepository;
    @Autowired private com.trainconcierge.notification.NotificationRepository notificationRepository;

    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    private User user1;
    private User user2;
    private String token1;
    private String token2;
    private Train train;
    private TrainSchedule schedule;
    private Journey journey1;
    private Journey journey2;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        travelCoordinationRecordRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        scheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Passenger")
                .email("alice@test.com")
                .passwordHash(passwordEncoder.encode("Alice@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("Bob")
                .lastName("Traveler")
                .email("bob@test.com")
                .passwordHash(passwordEncoder.encode("Bob@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        token1 = createToken(user1.getEmail(), user1.getRole(), user1.getId());
        token2 = createToken(user2.getEmail(), user2.getRole(), user2.getId());

        train = trainRepository.save(Train.builder()
                .trainNumber("TR100")
                .trainName("InterCity Express")
                .originStation("London Euston")
                .destinationStation("Manchester Piccadilly")
                .totalSeats(300)
                .active(true)
                .build());

        schedule = scheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(LocalDate.now().plusDays(1))
                .scheduledDeparture(LocalTime.of(9, 0))
                .scheduledArrival(LocalTime.of(11, 15))
                .platform("4")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.ON_TIME)
                .baseFare(BigDecimal.valueOf(85.00))
                .build());

        journey1 = journeyRepository.save(Journey.builder()
                .user(user1)
                .originStation("London Euston")
                .destinationStation("Manchester Piccadilly")
                .travelDate(LocalDate.now().plusDays(1))
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(85.00))
                .currency("GBP")
                .build());

        bookingRepository.save(Booking.builder()
                .bookingReference("TC-DIS-001")
                .user(user1)
                .schedule(schedule)
                .journey(journey1)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(85.00))
                .baseFare(BigDecimal.valueOf(80.00))
                .currency("GBP")
                .build());

        journey2 = journeyRepository.save(Journey.builder()
                .user(user2)
                .originStation("London Euston")
                .destinationStation("Manchester Piccadilly")
                .travelDate(LocalDate.now().plusDays(1))
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(85.00))
                .currency("GBP")
                .build());

        bookingRepository.save(Booking.builder()
                .bookingReference("TC-DIS-002")
                .user(user2)
                .schedule(schedule)
                .journey(journey2)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(85.00))
                .baseFare(BigDecimal.valueOf(80.00))
                .currency("GBP")
                .build());
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

    // ─────────────────────────────────────────────────────────────────────────
    // Disruption Detection Conditions
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void trainCancellation_CreatesCriticalDisruptionEvents_AndMarksJourneysDisrupted() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.CANCELLED)
                .delayMinutes(0)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);

        assertNotNull(event);
        assertEquals(DisruptionType.CANCELLATION, event.getType());
        assertEquals(DisruptionSeverity.CRITICAL, event.getSeverity());
        assertEquals(DisruptionStatus.DETECTED, event.getStatus());

        List<DisruptionEvent> events = disruptionEventRepository.findBySchedule(schedule);
        assertEquals(2, events.size(), "Each affected journey receives a DisruptionEvent");

        Journey updatedJ1 = journeyRepository.findById(journey1.getId()).orElseThrow();
        assertEquals(JourneyStatus.DISRUPTED, updatedJ1.getStatus());

        Journey updatedJ2 = journeyRepository.findById(journey2.getId()).orElseThrow();
        assertEquals(JourneyStatus.DISRUPTED, updatedJ2.getStatus());
    }

    @Test
    @Order(2)
    void minorDelay_UnderMinThreshold_IsIgnored() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(10) // default min threshold is 15 min
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);

        assertNull(event, "Delay below min threshold should not create a disruption event");
        assertEquals(0, disruptionEventRepository.count());

        Journey j1 = journeyRepository.findById(journey1.getId()).orElseThrow();
        assertEquals(JourneyStatus.PLANNED, j1.getStatus());
    }

    @Test
    @Order(3)
    void mediumDelay_CreatesMediumDisruptionEvent() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(25) // 15-60 min threshold -> MEDIUM
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);

        assertNotNull(event);
        assertEquals(DisruptionType.DELAY, event.getType());
        assertEquals(DisruptionSeverity.MEDIUM, event.getSeverity());
        assertEquals(DisruptionStatus.DETECTED, event.getStatus());

        Journey j1 = journeyRepository.findById(journey1.getId()).orElseThrow();
        assertEquals(JourneyStatus.DISRUPTED, j1.getStatus());
    }

    @Test
    @Order(4)
    void highDelay_CreatesHighDisruptionEvent() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(75) // >60 min threshold -> HIGH
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);

        assertNotNull(event);
        assertEquals(DisruptionType.DELAY, event.getType());
        assertEquals(DisruptionSeverity.HIGH, event.getSeverity());
        assertEquals(DisruptionStatus.DETECTED, event.getStatus());
    }

    @Test
    @Order(5)
    void platformChange_CreatesInformationalEvent_WithoutMarkingJourneyDisrupted() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.PLATFORM_CHANGED)
                .delayMinutes(0)
                .platform("9B")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);

        assertNotNull(event);
        assertEquals(DisruptionType.PLATFORM_CHANGE, event.getType());
        assertEquals(DisruptionSeverity.LOW, event.getSeverity());
        assertEquals("9B", event.getPlatform());

        Journey j1 = journeyRepository.findById(journey1.getId()).orElseThrow();
        assertEquals(JourneyStatus.PLANNED, j1.getStatus(), "Platform change should not disrupt journey status");
    }

    @Test
    @Order(6)
    void duplicatePrevention_SuppressesDuplicateOpenDisruptionEvents() {
        MonitoredStatusChange change1 = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(30)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        detectionService.handle(change1);
        assertEquals(2, disruptionEventRepository.count(), "Initial disruption created 2 events (one per journey)");

        // Second status change while first is still open
        MonitoredStatusChange change2 = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.DELAYED)
                .newStatus(TrainStatus.CANCELLED)
                .delayMinutes(0)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent secondResult = detectionService.handle(change2);

        assertNull(secondResult, "Duplicate open disruption should be suppressed");
        assertEquals(2, disruptionEventRepository.count(), "Event count remains 2");
    }

    @Test
    @Order(7)
    void eventLifecycleStateUpdates_TransitionsStatusCorrectly() {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.CANCELLED)
                .delayMinutes(0)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        DisruptionEvent event = detectionService.handle(change);
        Long eventId = event.getId();

        assertEquals(DisruptionStatus.DETECTED, event.getStatus());

        // Update to PROCESSING
        DisruptionEvent processingEvent = detectionService.updateEventStatus(eventId, DisruptionStatus.PROCESSING);
        assertEquals(DisruptionStatus.PROCESSING, processingEvent.getStatus());

        // Update to RESOLVED
        DisruptionEvent resolvedEvent = detectionService.updateEventStatus(eventId, DisruptionStatus.RESOLVED);
        assertEquals(DisruptionStatus.RESOLVED, resolvedEvent.getStatus());
        assertTrue(resolvedEvent.isResolved());
        assertNotNull(resolvedEvent.getResolvedAt());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST API Integration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    void getMyDisruptions_ReturnsUserDisruptions() throws Exception {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.CANCELLED)
                .delayMinutes(0)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        detectionService.handle(change);

        mockMvc.perform(get("/api/disruptions/my")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].trainNumber", is("TR100")))
                .andExpect(jsonPath("$.data[0].type", is("CANCELLATION")))
                .andExpect(jsonPath("$.data[0].severity", is("CRITICAL")))
                .andExpect(jsonPath("$.data[0].status", is("DETECTED")));
    }

    @Test
    @Order(9)
    void getDisruptionById_OwnedByUser_ReturnsDisruptionDetails() throws Exception {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(40)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        detectionService.handle(change);

        List<DisruptionEvent> aliceEvents = disruptionEventRepository.findByJourneyUserIdOrderByDetectedAtDesc(user1.getId());
        Long aliceEventId = aliceEvents.get(0).getId();

        mockMvc.perform(get("/api/disruptions/{id}", aliceEventId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.id", is(aliceEventId.intValue())))
                .andExpect(jsonPath("$.data.type", is("DELAY")))
                .andExpect(jsonPath("$.data.severity", is("MEDIUM")));
    }

    @Test
    @Order(10)
    void getDisruptionById_OwnedByAnotherUser_ReturnsForbidden() throws Exception {
        MonitoredStatusChange change = MonitoredStatusChange.builder()
                .scheduleId(schedule.getId())
                .trainNumber("TR100")
                .previousStatus(TrainStatus.ON_TIME)
                .newStatus(TrainStatus.DELAYED)
                .delayMinutes(40)
                .platform("4")
                .detectedAt(Instant.now())
                .build();

        detectionService.handle(change);

        List<DisruptionEvent> aliceEvents = disruptionEventRepository.findByJourneyUserIdOrderByDetectedAtDesc(user1.getId());
        Long aliceEventId = aliceEvents.get(0).getId();

        // Bob (token2) attempts to access Alice's disruption event
        mockMvc.perform(get("/api/disruptions/{id}", aliceEventId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(11)
    void getDisruptionById_NonExistentId_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/disruptions/999999")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }
}
