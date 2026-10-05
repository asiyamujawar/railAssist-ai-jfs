package com.trainconcierge.cab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.cab.dto.CabBookingResponse;
import com.trainconcierge.cab.dto.CreateCabBookingRequest;
import com.trainconcierge.cab.dto.RescheduleCabRequest;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelModificationAuditRepository;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
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
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CabBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CabBookingRepository cabBookingRepository;

    @Autowired
    private CabModificationAuditRepository cabAuditRepository;

    @Autowired
    private HotelBookingRepository hotelBookingRepository;

    @Autowired
    private HotelModificationAuditRepository hotelAuditRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainScheduleRepository trainScheduleRepository;

    @Autowired
    private SeatAvailabilityRepository seatAvailabilityRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private com.trainconcierge.rebooking.RebookingHistoryRepository rebookingHistoryRepository;

    @Autowired
    private com.trainconcierge.coordination.TravelCoordinationRecordRepository travelCoordinationRecordRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final String CABS_BASE = "/api/cabs";

    private static final String USER_A_EMAIL = "user.a.cab@example.com";
    private static final String USER_B_EMAIL = "user.b.cab@example.com";
    private static final String TEST_PASSWORD = "Cab@2026";

    private String userAToken;
    private String userBToken;

    private Journey userAJourney;
    private Journey userBJourney;

    private static final LocalDateTime FUTURE_PICKUP_1 = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MINUTES);
    private static final LocalDateTime FUTURE_PICKUP_2 = LocalDateTime.now().plusDays(4).truncatedTo(ChronoUnit.MINUTES);
    private static final LocalDateTime PAST_PICKUP = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MINUTES);

    @BeforeEach
    void setUp() {
        cleanDatabase();

        userAToken = createUserAndGetToken(USER_A_EMAIL, UserRole.ROLE_USER);
        userBToken = createUserAndGetToken(USER_B_EMAIL, UserRole.ROLE_USER);

        User userA = userRepository.findByEmail(USER_A_EMAIL.toLowerCase()).orElseThrow();
        User userB = userRepository.findByEmail(USER_B_EMAIL.toLowerCase()).orElseThrow();

        Train train = createAndSaveTrain("CB001", "Cab Train",
                "Mumbai Central", "Delhi Nizamuddin", 120, true);
        TrainSchedule schedule = createAndSaveSchedule(train, LocalDate.now());
        createSeatAvailabilities(schedule);

        userAJourney = journeyRepository.save(Journey.builder()
                .user(userA)
                .originStation("Mumbai Central")
                .destinationStation("Delhi Nizamuddin")
                .travelDate(LocalDate.now())
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(1200.00))
                .currency("INR")
                .build());

        userBJourney = journeyRepository.save(Journey.builder()
                .user(userB)
                .originStation("Bengaluru City")
                .destinationStation("Chennai Central")
                .travelDate(LocalDate.now())
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(800.00))
                .currency("INR")
                .build());
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
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        trainScheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .phoneNumber("+91-90000-54321")
                .role(role)
                .enabled(true)
                .build();
        User saved = userRepository.save(user);
        return buildToken(saved.getEmail(), role, saved.getId());
    }

    private Train createAndSaveTrain(String number, String name,
                                      String origin, String dest, int capacity,
                                      boolean active) {
        Train t = Train.builder()
                .trainNumber(number)
                .trainName(name)
                .originStation(origin)
                .destinationStation(dest)
                .totalSeats(capacity)
                .active(active)
                .build();
        return trainRepository.save(t);
    }

    private TrainSchedule createAndSaveSchedule(Train train, LocalDate date) {
        TrainSchedule s = TrainSchedule.builder()
                .train(train)
                .scheduledDate(date)
                .scheduledDeparture(LocalTime.of(10, 0))
                .scheduledArrival(LocalTime.of(16, 30))
                .baseFare(BigDecimal.valueOf(600.00))
                .platform("5")
                .scheduleStatus(com.trainconcierge.schedule.ScheduleStatus.SCHEDULED)
                .cancelled(false)
                .build();
        return trainScheduleRepository.save(s);
    }

    private void createSeatAvailabilities(TrainSchedule s) {
        var saSecond = SeatAvailability.builder()
                .schedule(s).seatClass(SeatClass.SECOND)
                .totalSeats(50).availableSeats(50).bookedSeats(0)
                .fare(BigDecimal.valueOf(200.00))
                .build();
        seatAvailabilityRepository.save(saSecond);
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

    private static CreateCabBookingRequest validCab(Long journeyId, LocalDateTime pickupTime) {
        return CreateCabBookingRequest.builder()
                .journeyId(journeyId)
                .pickupAddress("Terminal 3, Delhi Airport")
                .dropoffAddress("Connaught Place, Delhi")
                .scheduledPickupTime(pickupTime)
                .cabType("SEDAN")
                .estimatedFare(BigDecimal.valueOf(45.00))
                .currency("GBP")
                .provider("MOCK_CAB")
                .build();
    }

    @Test
    @Order(1)
    void createCabBooking_Success_Reference_Status_Ref() throws Exception {
        CreateCabBookingRequest req = validCab(userAJourney.getId(), FUTURE_PICKUP_1);

        MvcResult result = mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED_SIMULATED"))
                .andExpect(jsonPath("$.data.cabBookingReference", startsWith("CAB-")))
                .andExpect(jsonPath("$.data.externalBookingRef", startsWith("SIM-CAB-")))
                .andExpect(jsonPath("$.data.userId").value(userAJourney.getUser().getId().intValue()))
                .andExpect(jsonPath("$.data.journeyId").value(userAJourney.getId().intValue()))
                .andExpect(jsonPath("$.data.pickupAddress").value("Terminal 3, Delhi Airport"))
                .andExpect(jsonPath("$.data.dropoffAddress").value("Connaught Place, Delhi"))
                .andExpect(jsonPath("$.data.cabType").value("SEDAN"))
                .andExpect(jsonPath("$.data.estimatedFare", is(closeTo(45.0, 0.001))))
                .andExpect(jsonPath("$.data.currency").value("GBP"))
                .andExpect(jsonPath("$.data.provider").value("MOCK_CAB"))
                .andExpect(jsonPath("$.data.confirmedAt").exists())
                .andExpect(jsonPath("$.data.rescheduledAt").doesNotExist())
                .andExpect(jsonPath("$.data.modifications").isEmpty())
                .andReturn();

        assertEquals(1, cabBookingRepository.count());
        Long savedId = objectMapper.readTree(
                        result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        CabBooking saved = cabBookingRepository.findByIdWithDetails(savedId)
                .orElseThrow();
        assertEquals(CabBookingStatus.CONFIRMED_SIMULATED, saved.getStatus());
        assertTrue(saved.getCabBookingReference().startsWith("CAB-"));
        assertTrue(saved.getExternalBookingRef().startsWith("SIM-CAB-"));
        assertTrue(saved.getModifications().isEmpty());
    }

    @Test
    @Order(2)
    void createCabBooking_Fails_WhenPickupInPast() throws Exception {
        CreateCabBookingRequest req = validCab(userAJourney.getId(), PAST_PICKUP);

        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.CAB_INVALID_PICKUP_TIME.name()));

        assertEquals(0, cabBookingRepository.count());
    }

    @Test
    @Order(3)
    void createCabBooking_Fails_WhenJourneyNotOwned() throws Exception {
        CreateCabBookingRequest req = validCab(userBJourney.getId(), FUTURE_PICKUP_1);

        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        assertEquals(0, cabBookingRepository.count());
    }

    @Test
    @Order(4)
    void createCabBooking_Fails_WhenJourneyAbsent() throws Exception {
        CreateCabBookingRequest req = validCab(999_999L, FUTURE_PICKUP_1);

        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.RESOURCE_NOT_FOUND.name()));

        assertEquals(0, cabBookingRepository.count());
    }

    @Test
    @Order(5)
    void getCabsForJourney_Ownership_Works() throws Exception {
        CreateCabBookingRequest reqA = validCab(userAJourney.getId(), FUTURE_PICKUP_1);
        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(CABS_BASE + "/journey/" + userAJourney.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].pickupAddress").value("Terminal 3, Delhi Airport"));

        mockMvc.perform(get(CABS_BASE + "/journey/" + userAJourney.getId())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        mockMvc.perform(get(CABS_BASE + "/journey/999999")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(6)
    void rescheduleCab_Success_AuditRecorded() throws Exception {
        MvcResult created = mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validCab(userAJourney.getId(), FUTURE_PICKUP_1))))
                .andExpect(status().isCreated())
                .andReturn();

        Long cabId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        RescheduleCabRequest rescheduleReq = RescheduleCabRequest.builder()
                .newScheduledPickupTime(FUTURE_PICKUP_2)
                .build();

        mockMvc.perform(patch(CABS_BASE + "/" + cabId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED_SIMULATED"))
                .andExpect(jsonPath("$.data.rescheduledAt").exists())
                .andExpect(jsonPath("$.data.modifications.length()").value(1))
                .andExpect(jsonPath("$.data.modifications[0].oldPickupTime",
                        startsWith(FUTURE_PICKUP_1.toString())))
                .andExpect(jsonPath("$.data.modifications[0].newPickupTime",
                        startsWith(FUTURE_PICKUP_2.toString())))
                .andExpect(jsonPath("$.data.modifications[0].rescheduledAt").exists())
                .andExpect(jsonPath("$.data.modifications[0].simulationProvider")
                        .value("MOCK"));

        CabBooking updated = cabBookingRepository.findByIdWithDetails(cabId).orElseThrow();
        assertEquals(CabBookingStatus.RESCHEDULED_SIMULATED, updated.getStatus());
        assertEquals(FUTURE_PICKUP_2, updated.getScheduledPickupTime());
        assertEquals(1, updated.getModifications().size());
        assertEquals(1, cabAuditRepository.count());
    }

    @Test
    @Order(7)
    void rescheduleCab_Fails_InvalidTime_And_IdenticalTime() throws Exception {
        MvcResult created = mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validCab(userAJourney.getId(), FUTURE_PICKUP_1))))
                .andExpect(status().isCreated())
                .andReturn();

        Long cabId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        RescheduleCabRequest pastReq = RescheduleCabRequest.builder()
                .newScheduledPickupTime(PAST_PICKUP)
                .build();

        mockMvc.perform(patch(CABS_BASE + "/" + cabId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pastReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.CAB_INVALID_PICKUP_TIME.name()));

        RescheduleCabRequest sameReq = RescheduleCabRequest.builder()
                .newScheduledPickupTime(FUTURE_PICKUP_1)
                .build();

        mockMvc.perform(patch(CABS_BASE + "/" + cabId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sameReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.CAB_RESCHEDULE_NO_CHANGES.name()));

        CabBooking unchanged = cabBookingRepository.findByIdWithDetails(cabId).orElseThrow();
        assertEquals(FUTURE_PICKUP_1, unchanged.getScheduledPickupTime());
        assertEquals(CabBookingStatus.CONFIRMED_SIMULATED, unchanged.getStatus());
        assertEquals(0, cabAuditRepository.count());
    }

    @Test
    @Order(8)
    void rescheduleCab_Fails_CrossUser() throws Exception {
        MvcResult created = mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validCab(userAJourney.getId(), FUTURE_PICKUP_1))))
                .andExpect(status().isCreated())
                .andReturn();

        Long cabId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        RescheduleCabRequest req = RescheduleCabRequest.builder()
                .newScheduledPickupTime(FUTURE_PICKUP_2)
                .build();

        mockMvc.perform(patch(CABS_BASE + "/" + cabId + "/reschedule")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        CabBooking unchanged = cabBookingRepository.findByIdWithDetails(cabId).orElseThrow();
        assertEquals(FUTURE_PICKUP_1, unchanged.getScheduledPickupTime());
        assertEquals(CabBookingStatus.CONFIRMED_SIMULATED, unchanged.getStatus());
        assertEquals(0, cabAuditRepository.count());
    }

    @Test
    @Order(9)
    void createCabBooking_Fails_Unauthenticated_And_ValidationErrors() throws Exception {
        CreateCabBookingRequest req = validCab(userAJourney.getId(), FUTURE_PICKUP_1);

        mockMvc.perform(post(CABS_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        CreateCabBookingRequest nullJourney = CreateCabBookingRequest.builder()
                .journeyId(null)
                .pickupAddress("A")
                .dropoffAddress("B")
                .scheduledPickupTime(FUTURE_PICKUP_1)
                .build();
        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullJourney)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.journeyId").exists());

        CreateCabBookingRequest blankFields = CreateCabBookingRequest.builder()
                .journeyId(userAJourney.getId())
                .pickupAddress("")
                .dropoffAddress("")
                .scheduledPickupTime(FUTURE_PICKUP_1)
                .build();
        mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankFields)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.pickupAddress").exists())
                .andExpect(jsonPath("$.validationErrors.dropoffAddress").exists());
    }

    @Test
    @Order(10)
    void createCabBooking_StatusNeverStoresPlainConfirmed_AndRescheduleNoTimeIsError() throws Exception {
        MvcResult created = mockMvc.perform(post(CABS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validCab(userAJourney.getId(), FUTURE_PICKUP_1))))
                .andExpect(status().isCreated())
                .andReturn();

        Long cabId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        List<CabBooking> all = cabBookingRepository.findAll();
        for (CabBooking cb : all) {
            assertNotEquals("CONFIRMED", cb.getStatus().name());
            assertNotEquals("RESCHEDULED", cb.getStatus().name());
            assertTrue(cb.getStatus().name().endsWith("_SIMULATED"));
        }

        mockMvc.perform(patch(CABS_BASE + "/" + cabId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RescheduleCabRequest.builder().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.CAB_INVALID_PICKUP_TIME.name()));
    }
}
