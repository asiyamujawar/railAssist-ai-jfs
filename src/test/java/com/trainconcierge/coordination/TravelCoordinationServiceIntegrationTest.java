package com.trainconcierge.coordination;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.cab.*;
import com.trainconcierge.disruption.*;
import com.trainconcierge.hotel.*;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.rebooking.RebookingStatus;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test suite for TravelCoordinationService and TravelCoordinationController.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TravelCoordinationServiceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TravelCoordinationService coordinationService;
    @Autowired private TravelCoordinationRecordRepository coordinationRecordRepository;
    @Autowired private RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private HotelBookingRepository hotelBookingRepository;
    @Autowired private CabBookingRepository cabBookingRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private TrainRepository trainRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}") private String jwtSecret;
    @Value("${jwt.expiration-ms}") private long jwtExpirationMs;

    private User user1;
    private User user2;
    private String token1;
    private String token2;
    private Train trainOrig;
    private Train trainNew;
    private TrainSchedule origSchedule;
    private TrainSchedule newSchedule;
    private Journey journey;
    private Booking origBooking;
    private Booking newBooking;
    private DisruptionEvent disruption;
    private Recommendation recommendation;
    private RebookingHistory rebookingHistory;

    @BeforeEach
    void setUp() {
        coordinationRecordRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        cabBookingRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        scheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        user1 = userRepository.save(User.builder()
                .firstName("Coordination")
                .lastName("User1")
                .email("coord.user1@test.com")
                .passwordHash(passwordEncoder.encode("Coord@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        user2 = userRepository.save(User.builder()
                .firstName("Coordination")
                .lastName("User2")
                .email("coord.user2@test.com")
                .passwordHash(passwordEncoder.encode("Coord@2026!"))
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        token1 = createToken(user1.getEmail(), user1.getRole(), user1.getId());
        token2 = createToken(user2.getEmail(), user2.getRole(), user2.getId());

        trainOrig = trainRepository.save(Train.builder()
                .trainNumber("TR-101")
                .trainName("Disrupted Express")
                .originStation("London")
                .destinationStation("Edinburgh")
                .totalSeats(300)
                .active(true)
                .build());

        trainNew = trainRepository.save(Train.builder()
                .trainNumber("TR-202")
                .trainName("Replacement Express")
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

        newSchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(trainNew)
                .scheduledDate(travelDate)
                .scheduledDeparture(LocalTime.of(14, 0))
                .scheduledArrival(LocalTime.of(18, 0)) // 6h later arrival
                .platform("4")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(110.00))
                .build());

        journey = journeyRepository.save(Journey.builder()
                .user(user1)
                .originStation("London")
                .destinationStation("Edinburgh")
                .travelDate(travelDate)
                .status(JourneyStatus.REBOOKED)
                .totalCost(BigDecimal.valueOf(110.00))
                .currency("GBP")
                .build());

        origBooking = bookingRepository.save(Booking.builder()
                .bookingReference("TC-COORD-001")
                .user(user1)
                .schedule(origSchedule)
                .journey(journey)
                .status(BookingStatus.REBOOKED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(100.00))
                .baseFare(BigDecimal.valueOf(90.00))
                .currency("GBP")
                .build());

        newBooking = bookingRepository.save(Booking.builder()
                .bookingReference("TC-COORD-002")
                .user(user1)
                .schedule(newSchedule)
                .journey(journey)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .totalFare(BigDecimal.valueOf(110.00))
                .baseFare(BigDecimal.valueOf(100.00))
                .currency("GBP")
                .build());

        disruption = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(origSchedule)
                .journey(journey)
                .type(DisruptionType.CANCELLATION)
                .severity(DisruptionSeverity.CRITICAL)
                .status(DisruptionStatus.RESOLVED)
                .detectedAt(Instant.now())
                .resolvedAt(Instant.now())
                .description("Train TR-101 CANCELLED.")
                .estimatedDelayMinutes(0)
                .trainNumber("TR-101")
                .resolved(true)
                .rebookingTriggered(true)
                .build());

        recommendation = recommendationRepository.save(Recommendation.builder()
                .disruptionEvent(disruption)
                .user(user1)
                .suggestedSchedule(newSchedule)
                .score(BigDecimal.valueOf(0.95))
                .reason("Best match alternative")
                .status("ACCEPTED")
                .build());

        rebookingHistory = rebookingHistoryRepository.save(RebookingHistory.builder()
                .user(user1)
                .disruptionEvent(disruption)
                .journey(journey)
                .originalBooking(origBooking)
                .newBooking(newBooking)
                .recommendation(recommendation)
                .status(RebookingStatus.COMPLETED)
                .autonomous(false)
                .fareDifference(BigDecimal.valueOf(10.00))
                .initiatedAt(Instant.now())
                .completedAt(Instant.now())
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
    void coordinateTravel_SuccessfulUpdates_HotelAndCabRescheduledWithBuffers() {
        LocalDate travelDate = newSchedule.getScheduledDate();

        HotelBooking hotel = hotelBookingRepository.save(HotelBooking.builder()
                .user(user1)
                .journey(journey)
                .hotelName("Grand Edinburgh Hotel")
                .city("Edinburgh")
                .hotelAddress("10 Princes St")
                .checkInDate(travelDate.plusDays(1))
                .checkOutDate(travelDate.plusDays(3))
                .numberOfNights(2)
                .totalCost(BigDecimal.valueOf(200.00))
                .currency("GBP")
                .hotelBookingReference("HB-COORD-1")
                .status(HotelBookingStatus.CONFIRMED_SIMULATED)
                .confirmedAt(Instant.now())
                .build());

        LocalDateTime origCabPickup = LocalDateTime.of(travelDate, LocalTime.of(12, 15));
        CabBooking cab = cabBookingRepository.save(CabBooking.builder()
                .user(user1)
                .journey(journey)
                .pickupAddress("Edinburgh Waverley Station")
                .dropoffAddress("10 Princes St")
                .scheduledPickupTime(origCabPickup)
                .cabType("STANDARD")
                .estimatedFare(BigDecimal.valueOf(25.00))
                .currency("GBP")
                .provider("MOCK_CAB")
                .cabBookingReference("CB-COORD-1")
                .status(CabBookingStatus.CONFIRMED_SIMULATED)
                .confirmedAt(Instant.now())
                .build());

        TravelCoordinationResponse response = coordinationService.coordinateTravelForRebooking(rebookingHistory.getId());

        assertNotNull(response);
        assertEquals(CoordinationItemStatus.SUCCESS, response.getHotelUpdateStatus());
        assertEquals(CoordinationItemStatus.SUCCESS, response.getCabUpdateStatus());
        assertEquals(CoordinationOverallStatus.SUCCESS, response.getOverallStatus());

        // Verify revised times: Arrival at 18:00
        // Hotel check-in = 18:00 + 45m = 18:45 (Same day travelDate)
        // Cab pickup = 18:00 + 15m = 18:15
        assertEquals(travelDate, response.getHotelNewCheckIn());
        assertEquals(travelDate.plusDays(2), response.getHotelNewCheckOut());
        assertEquals(LocalDateTime.of(travelDate, LocalTime.of(18, 15)), response.getCabNewPickupTime());

        // Verify DB updates
        HotelBooking updatedHotel = hotelBookingRepository.findById(hotel.getId()).orElseThrow();
        assertEquals(HotelBookingStatus.RESCHEDULED_SIMULATED, updatedHotel.getStatus());

        CabBooking updatedCab = cabBookingRepository.findById(cab.getId()).orElseThrow();
        assertEquals(CabBookingStatus.RESCHEDULED_SIMULATED, updatedCab.getStatus());
        assertEquals(LocalDateTime.of(travelDate, LocalTime.of(18, 15)), updatedCab.getScheduledPickupTime());
    }

    @Test
    @Order(2)
    void coordinateTravel_MissingReservations_ReturnsNoActionRequired() {
        TravelCoordinationResponse response = coordinationService.coordinateTravelForRebooking(rebookingHistory.getId());

        assertNotNull(response);
        assertEquals(CoordinationItemStatus.NO_RESERVATION, response.getHotelUpdateStatus());
        assertEquals(CoordinationItemStatus.NO_RESERVATION, response.getCabUpdateStatus());
        assertEquals(CoordinationOverallStatus.NO_ACTION_REQUIRED, response.getOverallStatus());
        assertNull(response.getFailureDetails());
    }

    @Test
    @Order(3)
    void coordinateTravel_DuplicateExecution_ThrowsBusinessRuleException() {
        coordinationService.coordinateTravelForRebooking(rebookingHistory.getId());

        assertThrows(RuntimeException.class, () ->
                coordinationService.coordinateTravelForRebooking(rebookingHistory.getId()));
    }

    @Test
    @Order(4)
    void postCoordinationTriggerEndpoint_ManuallyTriggersWorkflow() throws Exception {
        mockMvc.perform(post("/api/journeys/{id}/coordination/trigger", journey.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.overallStatus", is("NO_ACTION_REQUIRED")));
    }

    @Test
    @Order(5)
    void getCoordinationHistory_ReturnsAuditRecords() throws Exception {
        coordinationService.coordinateTravelForRebooking(rebookingHistory.getId());

        mockMvc.perform(get("/api/journeys/{id}/coordination", journey.getId())
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].overallStatus", is("NO_ACTION_REQUIRED")));
    }
}
