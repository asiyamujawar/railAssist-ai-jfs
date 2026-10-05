package com.trainconcierge.timeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.cab.CabBooking;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabModificationAudit;
import com.trainconcierge.cab.CabModificationAuditRepository;
import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.disruption.DisruptionSeverity;
import com.trainconcierge.disruption.DisruptionType;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.hotel.HotelBooking;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAudit;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.notification.Notification;
import com.trainconcierge.notification.NotificationChannel;
import com.trainconcierge.notification.NotificationRepository;
import com.trainconcierge.notification.NotificationType;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.rebooking.RebookingStatus;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.*;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import com.trainconcierge.train.TrainStatus;
import com.trainconcierge.train.TrainStatusHistory;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Date;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JourneyTimelineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainScheduleRepository trainScheduleRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelBookingRepository hotelBookingRepository;

    @Autowired
    private HotelModificationAuditRepository hotelModificationAuditRepository;

    @Autowired
    private CabBookingRepository cabBookingRepository;

    @Autowired
    private CabModificationAuditRepository cabModificationAuditRepository;

    @Autowired
    private SeatAlertSubscriptionRepository seatAlertSubscriptionRepository;

    @Autowired
    private SeatAlertHistoryRepository seatAlertHistoryRepository;

    @Autowired
    private TrainStatusHistoryRepository trainStatusHistoryRepository;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private RebookingHistoryRepository rebookingHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final String USER_A_EMAIL = "passenger.timeline.a@example.com";
    private static final String USER_B_EMAIL = "passenger.timeline.b@example.com";
    private static final String TEST_PASSWORD = "Password123!";

    private String userAToken;
    private String userBToken;

    private User userA;
    private User userB;
    private Journey journeyA;
    private Journey journeyB;

    private static final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        trainStatusHistoryRepository.deleteAll();
        seatAlertHistoryRepository.deleteAll();
        seatAlertSubscriptionRepository.deleteAll();
        cabModificationAuditRepository.deleteAll();
        cabBookingRepository.deleteAll();
        hotelModificationAuditRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        trainScheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        userAToken = createUserAndGetToken(USER_A_EMAIL, UserRole.ROLE_USER);
        userBToken = createUserAndGetToken(USER_B_EMAIL, UserRole.ROLE_USER);

        userA = userRepository.findByEmail(USER_A_EMAIL.toLowerCase()).orElseThrow();
        userB = userRepository.findByEmail(USER_B_EMAIL.toLowerCase()).orElseThrow();

        journeyA = journeyRepository.save(Journey.builder()
                .user(userA)
                .originStation("London Euston")
                .destinationStation("Edinburgh Waverley")
                .travelDate(TODAY)
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(180.00))
                .currency("GBP")
                .build());

        journeyB = journeyRepository.save(Journey.builder()
                .user(userB)
                .originStation("Manchester Piccadilly")
                .destinationStation("Liverpool Lime Street")
                .travelDate(TODAY)
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(45.00))
                .currency("GBP")
                .build());
    }

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName("Test")
                .lastName("Passenger")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .phoneNumber("+44-7000-000000")
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

    @Test
    @Order(1)
    void getJourneyTimeline_CompleteWorkflow_All11Events_SortedChronologically() throws Exception {
        Instant baseTime = Instant.parse("2026-10-03T10:00:00Z");

        Train train = trainRepository.save(Train.builder()
                .trainNumber("LNX100")
                .trainName("London Express")
                .originStation("London Euston")
                .destinationStation("Edinburgh Waverley")
                .totalSeats(200)
                .active(true)
                .build());

        TrainSchedule schedule = trainScheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(TODAY)
                .scheduledDeparture(LocalTime.of(10, 0))
                .scheduledArrival(LocalTime.of(14, 30))
                .baseFare(BigDecimal.valueOf(150.00))
                .platform("3")
                .scheduleStatus(com.trainconcierge.schedule.ScheduleStatus.SCHEDULED)
                .cancelled(false)
                .build());

        // 1. T+10m: Booking Created
        Booking booking = bookingRepository.save(Booking.builder()
                .bookingReference("TC-20261003-0001")
                .user(userA)
                .schedule(schedule)
                .journey(journeyA)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.FIRST)
                .numberOfSeats(2)
                .baseFare(BigDecimal.valueOf(150.00))
                .totalFare(BigDecimal.valueOf(150.00))
                .currency("GBP")
                .confirmedAt(baseTime.plusSeconds(600))
                .build());

        // 2. T+20m: Hotel Added
        HotelBooking hotelBooking = hotelBookingRepository.save(HotelBooking.builder()
                .user(userA)
                .journey(journeyA)
                .hotelName("The Balmoral")
                .hotelAddress("1 Princes Street, Edinburgh EH1 2EQ")
                .city("Edinburgh")
                .checkInDate(TODAY)
                .checkOutDate(TODAY.plusDays(2))
                .numberOfNights(2)
                .totalCost(BigDecimal.valueOf(250.00))
                .currency("GBP")
                .hotelBookingReference("HOTEL-20261003-101")
                .confirmedAt(baseTime.plusSeconds(1200))
                .build());

        // 3. T+30m: Cab Added
        CabBooking cabBooking = cabBookingRepository.save(CabBooking.builder()
                .user(userA)
                .journey(journeyA)
                .pickupAddress("Edinburgh Waverley Station")
                .dropoffAddress("The Balmoral Hotel")
                .scheduledPickupTime(LocalDateTime.of(TODAY, LocalTime.of(15, 0)))
                .estimatedFare(BigDecimal.valueOf(25.00))
                .currency("GBP")
                .provider("MOCK_CAB")
                .cabBookingReference("CAB-20261003-201")
                .confirmedAt(baseTime.plusSeconds(1800))
                .build());

        // 4. T+40m: Seat Alert Generated
        SeatAlertSubscription sub = seatAlertSubscriptionRepository.save(SeatAlertSubscription.builder()
                .user(userA)
                .schedule(schedule)
                .seatClass(SeatClass.FIRST)
                .active(true)
                .threshold(5)
                .build());

        seatAlertHistoryRepository.save(SeatAlertHistory.builder()
                .subscription(sub)
                .user(userA)
                .availableSeatsAtAlert(3)
                .threshold(5)
                .alertedAt(baseTime.plusSeconds(2400))
                .message("3 seats opened up in FIRST class.")
                .build());

        // 5. T+50m: Train Status Changed
        trainStatusHistoryRepository.save(TrainStatusHistory.builder()
                .schedule(schedule)
                .status(TrainStatus.DELAYED)
                .recordedAt(baseTime.plusSeconds(3000))
                .delayMinutes(45)
                .message("Signal failure near Peterborough")
                .recordedAtStation("Peterborough")
                .simulated(true)
                .build());

        // 6. T+60m: Disruption Detected
        DisruptionEvent disruption = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(schedule)
                .journey(journeyA)
                .type(DisruptionType.DELAY)
                .severity(DisruptionSeverity.HIGH)
                .detectedAt(baseTime.plusSeconds(3600))
                .description("45-minute delay due to signal failure")
                .estimatedDelayMinutes(45)
                .build());

        // 7. T+70m: AI Recommendation Generated
        Train altTrain = trainRepository.save(Train.builder()
                .trainNumber("LNX102")
                .trainName("Edinburgh Express")
                .originStation("London Euston")
                .destinationStation("Edinburgh Waverley")
                .totalSeats(200)
                .active(true)
                .build());

        TrainSchedule altSchedule = trainScheduleRepository.save(TrainSchedule.builder()
                .train(altTrain)
                .scheduledDate(TODAY)
                .scheduledDeparture(LocalTime.of(11, 0))
                .scheduledArrival(LocalTime.of(15, 15))
                .baseFare(BigDecimal.valueOf(150.00))
                .scheduleStatus(com.trainconcierge.schedule.ScheduleStatus.SCHEDULED)
                .build());

        Recommendation rec = recommendationRepository.save(Recommendation.builder()
                .disruptionEvent(disruption)
                .user(userA)
                .suggestedSchedule(altSchedule)
                .score(BigDecimal.valueOf(0.950))
                .reason("Next direct service departing at 11:00")
                .status("ACCEPTED")
                .respondedAt(baseTime.plusSeconds(4200))
                .build());

        // 8. T+80m: Rebooking Completed
        Booking newBooking = bookingRepository.save(Booking.builder()
                .bookingReference("TC-20261003-0002")
                .user(userA)
                .schedule(altSchedule)
                .journey(journeyA)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.FIRST)
                .numberOfSeats(2)
                .baseFare(BigDecimal.valueOf(150.00))
                .totalFare(BigDecimal.valueOf(150.00))
                .currency("GBP")
                .confirmedAt(baseTime.plusSeconds(4800))
                .build());

        rebookingHistoryRepository.save(RebookingHistory.builder()
                .user(userA)
                .disruptionEvent(disruption)
                .journey(journeyA)
                .originalBooking(booking)
                .newBooking(newBooking)
                .recommendation(rec)
                .status(RebookingStatus.COMPLETED)
                .autonomous(true)
                .fareDifference(BigDecimal.ZERO)
                .initiatedAt(baseTime.plusSeconds(4500))
                .completedAt(baseTime.plusSeconds(4800))
                .build());

        // 9. T+90m: Hotel Rescheduled Audit
        hotelModificationAuditRepository.save(HotelModificationAudit.builder()
                .hotelBooking(hotelBooking)
                .oldCheckInDate(TODAY)
                .newCheckInDate(TODAY.plusDays(1))
                .oldCheckOutDate(TODAY.plusDays(2))
                .newCheckOutDate(TODAY.plusDays(3))
                .rescheduledAt(baseTime.plusSeconds(5400))
                .simulationProvider("MOCK")
                .build());

        // 10. T+100m: Cab Rescheduled Audit
        cabModificationAuditRepository.save(CabModificationAudit.builder()
                .cabBooking(cabBooking)
                .oldPickupTime(LocalDateTime.of(TODAY, LocalTime.of(15, 0)))
                .newPickupTime(LocalDateTime.of(TODAY, LocalTime.of(16, 0)))
                .rescheduledAt(baseTime.plusSeconds(6000))
                .simulationProvider("MOCK")
                .build());

        // 11. T+110m: Notification Generated
        notificationRepository.save(Notification.builder()
                .user(userA)
                .type(NotificationType.REBOOKING_CONFIRMED)
                .channel(NotificationChannel.IN_APP)
                .title("Rebooking Confirmed")
                .body("Your train booking TC-20261003-0001 has been rebooked to TC-20261003-0002.")
                .referenceId("TC-20261003-0001")
                .referenceType("Booking")
                .sent(true)
                .sentAt(baseTime.plusSeconds(6600))
                .read(false)
                .build());

        mockMvc.perform(get("/api/journeys/" + journeyA.getId() + "/timeline")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.journeyId").value(journeyA.getId().intValue()))
                .andExpect(jsonPath("$.data.originStation").value("London Euston"))
                .andExpect(jsonPath("$.data.destinationStation").value("Edinburgh Waverley"))
                .andExpect(jsonPath("$.data.totalEvents").value(12))
                .andExpect(jsonPath("$.data.events[0].eventType").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$.data.events[1].eventType").value("HOTEL_ADDED"))
                .andExpect(jsonPath("$.data.events[2].eventType").value("CAB_ADDED"))
                .andExpect(jsonPath("$.data.events[3].eventType").value("SEAT_ALERT_GENERATED"))
                .andExpect(jsonPath("$.data.events[4].eventType").value("TRAIN_STATUS_CHANGED"))
                .andExpect(jsonPath("$.data.events[5].eventType").value("DISRUPTION_DETECTED"))
                .andExpect(jsonPath("$.data.events[6].eventType").value("RECOMMENDATION_GENERATED"))
                .andExpect(jsonPath("$.data.events[7].eventType").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$.data.events[8].eventType").value("REBOOKING_COMPLETED"))
                .andExpect(jsonPath("$.data.events[9].eventType").value("HOTEL_RESCHEDULED"))
                .andExpect(jsonPath("$.data.events[10].eventType").value("CAB_RESCHEDULED"))
                .andExpect(jsonPath("$.data.events[11].eventType").value("NOTIFICATION_GENERATED"))
                .andExpect(jsonPath("$.data.events[0].explanation", containsString("Passenger created train booking TC-20261003-0001")))
                .andExpect(jsonPath("$.data.events[5].explanation", containsString("System detected a HIGH DELAY disruption")))
                .andExpect(jsonPath("$.data.events[8].explanation", containsString("Disruption recovery workflow automatically processed rebooking")));
    }

    @Test
    @Order(2)
    void getJourneyTimeline_HandlesMissingOptionalRecords_WithoutError() throws Exception {
        // Journey with ONLY a booking (no hotel, cab, seat alerts, disruptions, etc.)
        Train train = trainRepository.save(Train.builder()
                .trainNumber("MCR500")
                .trainName("Manchester Local")
                .originStation("Manchester Piccadilly")
                .destinationStation("Liverpool Lime Street")
                .totalSeats(100)
                .active(true)
                .build());

        TrainSchedule schedule = trainScheduleRepository.save(TrainSchedule.builder()
                .train(train)
                .scheduledDate(TODAY)
                .scheduledDeparture(LocalTime.of(8, 0))
                .scheduledArrival(LocalTime.of(9, 0))
                .baseFare(BigDecimal.valueOf(20.00))
                .scheduleStatus(com.trainconcierge.schedule.ScheduleStatus.SCHEDULED)
                .build());

        bookingRepository.save(Booking.builder()
                .bookingReference("TC-MAN-001")
                .user(userB)
                .schedule(schedule)
                .journey(journeyB)
                .status(BookingStatus.CONFIRMED)
                .seatClass(SeatClass.SECOND)
                .numberOfSeats(1)
                .baseFare(BigDecimal.valueOf(20.00))
                .totalFare(BigDecimal.valueOf(20.00))
                .currency("GBP")
                .confirmedAt(Instant.now())
                .build());

        mockMvc.perform(get("/api/journeys/" + journeyB.getId() + "/timeline")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.journeyId").value(journeyB.getId().intValue()))
                .andExpect(jsonPath("$.data.totalEvents").value(1))
                .andExpect(jsonPath("$.data.events[0].eventType").value("BOOKING_CREATED"))
                .andExpect(jsonPath("$.data.events[0].entityId").value("TC-MAN-001"));
    }

    @Test
    @Order(3)
    void getJourneyTimeline_EnforcesUserOwnership_Returns403Forbidden() throws Exception {
        // User B tries to access User A's journey timeline
        mockMvc.perform(get("/api/journeys/" + journeyA.getId() + "/timeline")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    @Order(4)
    void getJourneyTimeline_NonExistentJourney_Returns404NotFound() throws Exception {
        mockMvc.perform(get("/api/journeys/999999/timeline")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(5)
    void getJourneyTimeline_Unauthenticated_Returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/journeys/" + journeyA.getId() + "/timeline"))
                .andExpect(status().isUnauthorized());
    }
}
