package com.trainconcierge.rebooking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.disruption.*;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.rebooking.dto.RebookRequest;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.recommendation.RecommendationService;
import com.trainconcierge.recommendation.RecommendationResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Transactional and REST Integration tests for the Simulated Rebooking Service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RebookingServiceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RebookingService rebookingService;
    @Autowired private RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private RecommendationService recommendationService;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private SeatAvailabilityRepository seatAvailabilityRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private com.trainconcierge.coordination.TravelCoordinationRecordRepository travelCoordinationRecordRepository;

    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    private User user1;
    private User user2;
    private String token1;
    private String token2;
    private Train trainOrig;
    private Train trainAlt;
    private TrainSchedule origSchedule;
    private TrainSchedule altSchedule;
    private SeatAvailability altSeatAvail;
    private Journey journey;
    private Booking originalBooking;
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
                .firstName("Rebooking")
                .lastName("User1")
                .email("rebook.user1@test.com")
                .passwordHash(passwordEncoder.encode("Rebook@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("Rebooking")
                .lastName("User2")
                .email("rebook.user2@test.com")
                .passwordHash(passwordEncoder.encode("Rebook@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        token1 = createToken(user1.getEmail(), user1.getRole(), user1.getId());
        token2 = createToken(user2.getEmail(), user2.getRole(), user2.getId());

        trainOrig = trainRepository.save(Train.builder()
                .trainNumber("TR-100")
                .trainName("Disrupted Express")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        trainAlt = trainRepository.save(Train.builder()
                .trainNumber("TR-200")
                .trainName("Alternative Express")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        LocalDate travelDate = LocalDate.now().plusDays(1);

        origSchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(trainOrig)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(8, 0))
                .scheduledArrival(LocalTime.of(12, 0))
                .platform("1")
                .delayMinutes(0)
                .cancelled(true)
                .scheduleStatus(ScheduleStatus.CANCELLED)
                .baseFare(BigDecimal.valueOf(100.00))
                .build());

        altSchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(trainAlt)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(9, 0))
                .scheduledArrival(LocalTime.of(13, 0))
                .platform("3")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(110.00))
                .build());

        altSeatAvail = seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(altSchedule)
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(40)
                .bookedSeats(60)
                .fare(BigDecimal.valueOf(110.00))
                .build());

        journey = journeyRepository.save(Journey.builder()
                .user(user1)
                .originStation("London")
                .destinationStation("Edinburgh")
                .travelDate(travelDate)
                .status(JourneyStatus.DISRUPTED)
                .totalCost(BigDecimal.valueOf(100.00))
                .currency("GBP")
                .build());

        originalBooking = bookingRepository.save(Booking.builder()
                .bookingReference("TC-REBOOK-001")
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

        disruptionEvent = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(origSchedule)
                .journey(journey)
                .type(DisruptionType.CANCELLATION)
                .severity(DisruptionSeverity.CRITICAL)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(Instant.now())
                .description("Train TR-100 has been CANCELLED.")
                .estimatedDelayMinutes(0)
                .trainNumber("TR-100")
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

    @Test
    @Order(1)
    void rebook_UserApprovedMode_SuccessfullyRebooksAndUpdatesState() throws Exception {
        // Step 1: Generate recommendations first
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        assertFalse(recs.isEmpty());
        Long recId = recs.get(0).getId();

        // Step 2: Execute Rebooking via POST /api/disruptions/{id}/rebook
        RebookRequest request = RebookRequest.builder()
                .recommendationId(recId)
                .autoMode(false)
                .build();

        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.originalBookingReference", is("TC-REBOOK-001")))
                .andExpect(jsonPath("$.data.originalBookingStatus", is("REBOOKED")))
                .andExpect(jsonPath("$.data.newBookingStatus", is("CONFIRMED")))
                .andExpect(jsonPath("$.data.newTrainNumber", is("TR-200")))
                .andExpect(jsonPath("$.data.fareDifference", is(10.0)))
                .andExpect(jsonPath("$.data.autonomous", is(false)));

        // Step 3: Verify DB state updates
        Booking origUpdated = bookingRepository.findById(originalBooking.getId()).orElseThrow();
        assertEquals(BookingStatus.REBOOKED, origUpdated.getStatus(), "Original booking status must be REBOOKED");
        assertNotNull(origUpdated.getReplacementBooking(), "Replacement booking link must be set");

        Booking newBooking = bookingRepository.findByIdWithDetails(origUpdated.getReplacementBooking().getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, newBooking.getStatus());
        assertEquals("TR-200", newBooking.getSchedule().getTrain().getTrainNumber());
        assertEquals(1, newBooking.getNumberOfSeats());

        // Seat inventory decremented
        SeatAvailability updatedAvail = seatAvailabilityRepository.findById(altSeatAvail.getId()).orElseThrow();
        assertEquals(39, updatedAvail.getAvailableSeats(), "Available seats must be decremented by 1");
        assertEquals(61, updatedAvail.getBookedSeats(), "Booked seats must be incremented by 1");

        // Disruption & Journey resolved
        DisruptionEvent updatedDisruption = disruptionEventRepository.findById(disruptionEvent.getId()).orElseThrow();
        assertTrue(updatedDisruption.isResolved());
        assertEquals(DisruptionStatus.RESOLVED, updatedDisruption.getStatus());

        Journey updatedJourney = journeyRepository.findById(journey.getId()).orElseThrow();
        assertEquals(JourneyStatus.REBOOKED, updatedJourney.getStatus());

        // Audit History created
        List<RebookingHistory> historyList = rebookingHistoryRepository.findByJourney(journey);
        assertEquals(1, historyList.size());
        RebookingHistory history = historyList.get(0);
        assertEquals(RebookingStatus.COMPLETED, history.getStatus());
        assertFalse(history.isAutonomous());
        assertEquals(0, BigDecimal.valueOf(10.00).compareTo(history.getFareDifference()));
    }

    @Test
    @Order(2)
    void rebook_ControlledDemoAutoMode_SetsAutonomousFlagTrue() throws Exception {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        Long recId = recs.get(0).getId();

        RebookRequest request = RebookRequest.builder()
                .recommendationId(recId)
                .autoMode(true)
                .build();

        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.autonomous", is(true)));

        RebookingHistory history = rebookingHistoryRepository.findByJourney(journey).get(0);
        assertTrue(history.isAutonomous());
    }

    @Test
    @Order(3)
    void rebook_DuplicateAttempt_ReturnsUnprocessableEntity() throws Exception {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        Long recId = recs.get(0).getId();

        RebookRequest request = RebookRequest.builder().recommendationId(recId).build();

        // First rebooking succeeds
        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Duplicate rebooking attempt fails
        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("DISRUPTION_ALREADY_REBOOKED")));
    }

    @Test
    @Order(4)
    void rebook_InsufficientSeats_ReturnsUnprocessableEntity() throws Exception {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        Long recId = recs.get(0).getId();

        // Set available seats to 0
        altSeatAvail.setAvailableSeats(0);
        seatAvailabilityRepository.save(altSeatAvail);

        RebookRequest request = RebookRequest.builder().recommendationId(recId).build();

        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode", is("INSUFFICIENT_SEATS")));
    }

    @Test
    @Order(5)
    void rebook_DisruptionOfAnotherUser_ReturnsForbidden() throws Exception {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        Long recId = recs.get(0).getId();

        RebookRequest request = RebookRequest.builder().recommendationId(recId).build();

        // User 2 (Bob) tries to rebook User 1 (Alice)'s disruption
        mockMvc.perform(post("/api/disruptions/{id}/rebook", disruptionEvent.getId())
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void getRebookingHistory_ReturnsJourneyHistoryAuditTrail() throws Exception {
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruptionEvent.getId(), user1.getEmail());
        Long recId = recs.get(0).getId();

        RebookRequest request = RebookRequest.builder().recommendationId(recId).build();
        rebookingService.rebook(disruptionEvent.getId(), request, user1.getEmail());

        mockMvc.perform(get("/api/journeys/{id}/rebooking-history", journey.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].journeyId", is(journey.getId().intValue())))
                .andExpect(jsonPath("$.data[0].status", is("COMPLETED")))
                .andExpect(jsonPath("$.data[0].originalBookingReference", is("TC-REBOOK-001")));
    }
}
