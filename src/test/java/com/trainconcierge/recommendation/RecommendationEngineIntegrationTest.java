package com.trainconcierge.recommendation;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.disruption.*;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Alternative Train Recommendation Engine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecommendationEngineIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RecommendationService recommendationService;
    @Autowired private RecommendationScorer recommendationScorer;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private SeatAvailabilityRepository seatAvailabilityRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private com.trainconcierge.rebooking.RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private com.trainconcierge.coordination.TravelCoordinationRecordRepository travelCoordinationRecordRepository;

    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    private User user1;
    private User user2;
    private String token1;
    private String token2;
    private Train trainOrig;
    private Train trainAlt1;
    private Train trainAlt2;
    private TrainSchedule origSchedule;
    private TrainSchedule altSchedule1;
    private TrainSchedule altSchedule2;
    private DisruptionEvent disruptionEvent;

    @BeforeEach
    void setUp() {
        travelCoordinationRecordRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        scheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .firstName("Recommendation")
                .lastName("User1")
                .email("rec.user1@test.com")
                .passwordHash(passwordEncoder.encode("Rec@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("Recommendation")
                .lastName("User2")
                .email("rec.user2@test.com")
                .passwordHash(passwordEncoder.encode("Rec@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        token1 = createToken(user1.getEmail(), user1.getRole(), user1.getId());
        token2 = createToken(user2.getEmail(), user2.getRole(), user2.getId());

        // Trains
        trainOrig = trainRepository.save(Train.builder()
                .trainNumber("TR-ORIG")
                .trainName("Original Express")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        trainAlt1 = trainRepository.save(Train.builder()
                .trainNumber("TR-ALT1")
                .trainName("Fast Alternative")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        trainAlt2 = trainRepository.save(Train.builder()
                .trainNumber("TR-ALT2")
                .trainName("Slower Alternative")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        LocalDate travelDate = LocalDate.now().plusDays(1);

        // Schedules
        origSchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(trainOrig)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(8, 0))
                .scheduledArrival(LocalTime.of(12, 0)) // 4h duration
                .platform("1")
                .delayMinutes(0)
                .cancelled(true)
                .scheduleStatus(ScheduleStatus.CANCELLED)
                .baseFare(BigDecimal.valueOf(100.00))
                .build());

        altSchedule1 = scheduleRepository.save(TrainSchedule.builder()
                .train(trainAlt1)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(9, 0))
                .scheduledArrival(LocalTime.of(13, 0)) // 4h duration, 1h later arrival
                .platform("3")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(105.00)) // £5 more
                .build());

        altSchedule2 = scheduleRepository.save(TrainSchedule.builder()
                .train(trainAlt2)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(10, 0))
                .scheduledArrival(LocalTime.of(15, 0)) // 5h duration, 3h later arrival
                .platform("5")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(90.00)) // £10 cheaper
                .build());

        // Seat availability
        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(altSchedule1)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(40)
                .bookedSeats(60)
                .fare(BigDecimal.valueOf(105.00))
                .build());

        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(altSchedule2)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(20)
                .bookedSeats(80)
                .fare(BigDecimal.valueOf(90.00))
                .build());

        // Journey & Booking
        Journey journey = journeyRepository.save(Journey.builder()
                .user(user1)
                .originStation("London")
                .destinationStation("Edinburgh")
                .travelDate(travelDate)
                .status(JourneyStatus.DISRUPTED)
                .totalCost(BigDecimal.valueOf(100.00))
                .currency("GBP")
                .build());

        bookingRepository.save(Booking.builder()
                .bookingReference("TC-REC-001")
                .user(user1)
                .schedule(origSchedule)
                .journey(journey)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(100.00))
                .baseFare(BigDecimal.valueOf(90.00))
                .currency("GBP")
                .build());

        // Disruption Event
        disruptionEvent = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(origSchedule)
                .journey(journey)
                .type(DisruptionType.CANCELLATION)
                .severity(DisruptionSeverity.CRITICAL)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(Instant.now())
                .description("Train TR-ORIG has been CANCELLED.")
                .estimatedDelayMinutes(0)
                .trainNumber("TR-ORIG")
                .resolved(false)
                .rebookingTriggered(false)
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
    // Service Unit / Integration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void generateRecommendations_RanksAlternativesByScoreDescending() {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());

        assertNotNull(recs);
        assertEquals(2, recs.size());

        // First recommendation must have higher score than second
        assertTrue(recs.get(0).getScore() >= recs.get(1).getScore());
        assertEquals("TR-ALT1", recs.get(0).getTrainNumber(), "Faster/closer arrival alternative TR-ALT1 should rank first");
        assertEquals("TR-ALT2", recs.get(1).getTrainNumber());
    }

    @Test
    @Order(2)
    void generateRecommendations_FiltersOutCancelledAndNoSeatAlternatives() {
        // Add a cancelled alternative schedule
        Train trainCancelled = trainRepository.save(Train.builder()
                .trainNumber("TR-CANCELLED")
                .trainName("Cancelled Train")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        TrainSchedule schedCancelled = scheduleRepository.save(TrainSchedule.builder()
                .train(trainCancelled)
                .scheduledDate(origSchedule.getScheduledDate())
                .scheduledDeparture(LocalTime.of(9, 30))
                .scheduledArrival(LocalTime.of(13, 30))
                .platform("2")
                .delayMinutes(0)
                .cancelled(true)
                .scheduleStatus(ScheduleStatus.CANCELLED)
                .baseFare(BigDecimal.valueOf(95.00))
                .build());

        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(schedCancelled)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(50)
                .bookedSeats(50)
                .fare(BigDecimal.valueOf(95.00))
                .build());

        // Add a schedule with 0 available seats
        Train trainNoSeats = trainRepository.save(Train.builder()
                .trainNumber("TR-NOSEATS")
                .trainName("Full Train")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        TrainSchedule schedNoSeats = scheduleRepository.save(TrainSchedule.builder()
                .train(trainNoSeats)
                .scheduledDate(origSchedule.getScheduledDate())
                .scheduledDeparture(LocalTime.of(11, 0))
                .scheduledArrival(LocalTime.of(15, 0))
                .platform("6")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(95.00))
                .build());

        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(schedNoSeats)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(0) // 0 seats
                .bookedSeats(100)
                .fare(BigDecimal.valueOf(95.00))
                .build());

        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());

        // Only TR-ALT1 and TR-ALT2 should be returned (neither orig, cancelled, nor no-seats)
        assertEquals(2, recs.size());
        assertTrue(recs.stream().noneMatch(r -> r.getTrainNumber().equals("TR-ORIG")));
        assertTrue(recs.stream().noneMatch(r -> r.getTrainNumber().equals("TR-CANCELLED")));
        assertTrue(recs.stream().noneMatch(r -> r.getTrainNumber().equals("TR-NOSEATS")));
    }

    @Test
    @Order(3)
    void scoreNormalization_CalculatesExactWeightedScores() {
        RecommendationScorer.ScoreResult scoreResult = recommendationScorer.score(altSchedule1, origSchedule, 40);

        // altSchedule1:
        // Arrival diff: 60 min -> Arrival score: 100 - (60 * 0.5) = 70.0
        // Duration: 4h (same as orig) -> Duration score: 100.0
        // Fare diff: +£5.00 -> Fare score: 100 - (5 * 2.0) = 90.0
        // Seats: 40 seats -> Seats score: 40 * 2.0 = 80.0
        // Weighted: (70 * 0.35) + (100 * 0.25) + (90 * 0.20) + (80 * 0.20)
        //          = 24.5 + 25.0 + 18.0 + 16.0 = 83.50

        assertEquals(70.0, scoreResult.arrivalScore(), 0.01);
        assertEquals(100.0, scoreResult.durationScore(), 0.01);
        assertEquals(90.0, scoreResult.fareScore(), 0.01);
        assertEquals(80.0, scoreResult.seatsScore(), 0.01);
        assertEquals(83.50, scoreResult.totalScore100(), 0.01);
    }

    @Test
    @Order(4)
    void generateRecommendations_NoEligibleAlternatives_ReturnsEmptyList() {
        // Delete seat availability for all alternatives
        seatAvailabilityRepository.deleteAll();

        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());

        assertNotNull(recs);
        assertTrue(recs.isEmpty(), "No eligible alternatives should return empty list gracefully without throwing exception");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REST API Integration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    void postRecommendations_GeneratesAndReturnsRankedList() throws Exception {
        mockMvc.perform(post("/api/disruptions/{id}/recommendations", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].trainNumber", is("TR-ALT1")))
                .andExpect(jsonPath("$.data[0].score", greaterThan(80.0)))
                .andExpect(jsonPath("$.data[0].reason", containsString("TR-ALT1")))
                .andExpect(jsonPath("$.data[0].availableSeats", is(40)));

        // Verify recommendations persisted in DB
        List<Recommendation> saved = recommendationRepository.findByDisruptionEventOrderByScoreDesc(disruptionEvent);
        assertEquals(2, saved.size());
    }

    @Test
    @Order(6)
    void getRecommendations_ReturnsPersistedRankedList() throws Exception {
        // First generate recommendations
        recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());

        // Fetch via GET API
        mockMvc.perform(get("/api/disruptions/{id}/recommendations", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].trainNumber", is("TR-ALT1")));
    }

    @Test
    @Order(7)
    void recommendations_OwnedByAnotherUser_ReturnsForbidden() throws Exception {
        // User 2 (Bob) attempts to trigger/view recommendations for User 1 (Alice)'s disruption
        mockMvc.perform(post("/api/disruptions/{id}/recommendations", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/disruptions/{id}/recommendations", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(8)
    void recommendations_InvalidDisruptionId_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/disruptions/999999/recommendations")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }
}
