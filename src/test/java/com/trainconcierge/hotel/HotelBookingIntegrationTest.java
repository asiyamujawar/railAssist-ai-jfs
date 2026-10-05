package com.trainconcierge.hotel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.hotel.dto.CreateHotelBookingRequest;
import com.trainconcierge.hotel.dto.RescheduleHotelRequest;
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
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HotelBookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HotelBookingRepository hotelBookingRepository;

    @Autowired
    private HotelModificationAuditRepository auditRepository;

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

    private static final String HOTELS_BASE = "/api/hotels";

    private static final String USER_A_EMAIL = "user.a.hotel@example.com";
    private static final String USER_B_EMAIL = "user.b.hotel@example.com";
    private static final String TEST_PASSWORD = "Hotel@2026";

    private String userAToken;
    private String userBToken;

    private Journey userAJourney;
    private Journey userBJourney;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate NEXT_WEEK = TODAY.plusDays(7);
    private static final LocalDate IN_2_WEEKS = TODAY.plusDays(14);

    @Autowired
    private com.trainconcierge.notification.NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        travelCoordinationRecordRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        auditRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        trainScheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        userAToken = createUserAndGetToken(USER_A_EMAIL, UserRole.ROLE_USER);
        userBToken = createUserAndGetToken(USER_B_EMAIL, UserRole.ROLE_USER);

        User userA = userRepository.findByEmail(USER_A_EMAIL.toLowerCase()).orElseThrow();
        User userB = userRepository.findByEmail(USER_B_EMAIL.toLowerCase()).orElseThrow();

        Train train = createAndSaveTrain("HT001", "Hotel Train",
                "Mumbai Central", "Delhi Nizamuddin", 120, true);
        TrainSchedule schedule = createAndSaveSchedule(train, TODAY);
        createSeatAvailabilities(schedule);

        userAJourney = journeyRepository.save(Journey.builder()
                .user(userA)
                .originStation("Mumbai Central")
                .destinationStation("Delhi Nizamuddin")
                .travelDate(TODAY)
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(1200.00))
                .currency("INR")
                .build());

        userBJourney = journeyRepository.save(Journey.builder()
                .user(userB)
                .originStation("Bengaluru City")
                .destinationStation("Chennai Central")
                .travelDate(TODAY)
                .status(JourneyStatus.PLANNED)
                .totalCost(BigDecimal.valueOf(800.00))
                .currency("INR")
                .build());
    }

    @AfterEach
    void tearDown() {
        auditRepository.deleteAll();
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
                .phoneNumber("+91-90000-12345")
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

    private static CreateHotelBookingRequest validHotel(Long journeyId,
                                                        LocalDate checkIn, LocalDate checkOut) {
        return CreateHotelBookingRequest.builder()
                .journeyId(journeyId)
                .hotelName("The Gateway")
                .city("Delhi")
                .hotelAddress("12 Connaught Place, New Delhi 110001")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .totalCost(BigDecimal.valueOf(350.00))
                .currency("GBP")
                .build();
    }

    @Test
    @Order(1)
    void createHotelBooking_Success_Reference_Status_Ref() throws Exception {
        LocalDate checkIn = NEXT_WEEK;
        LocalDate checkOut = NEXT_WEEK.plusDays(2);
        CreateHotelBookingRequest req = validHotel(userAJourney.getId(), checkIn, checkOut);

        MvcResult result = mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED_SIMULATED"))
                .andExpect(jsonPath("$.data.hotelBookingReference", startsWith("HOTEL-")))
                .andExpect(jsonPath("$.data.externalBookingRef", startsWith("SIM-HOTEL-")))
                .andExpect(jsonPath("$.data.userId").value(userAJourney.getUser().getId().intValue()))
                .andExpect(jsonPath("$.data.journeyId").value(userAJourney.getId().intValue()))
                .andExpect(jsonPath("$.data.hotelName").value("The Gateway"))
                .andExpect(jsonPath("$.data.city").value("Delhi"))
                .andExpect(jsonPath("$.data.hotelAddress").value("12 Connaught Place, New Delhi 110001"))
                .andExpect(jsonPath("$.data.checkInDate").value(checkIn.toString()))
                .andExpect(jsonPath("$.data.checkOutDate").value(checkOut.toString()))
                .andExpect(jsonPath("$.data.numberOfNights").value(2))
                .andExpect(jsonPath("$.data.totalCost", is(closeTo(350.0, 0.001))))
                .andExpect(jsonPath("$.data.currency").value("GBP"))
                .andExpect(jsonPath("$.data.confirmedAt").exists())
                .andExpect(jsonPath("$.data.rescheduledAt").doesNotExist())
                .andExpect(jsonPath("$.data.modifications").isEmpty())
                .andReturn();

        assertEquals(1, hotelBookingRepository.count());
        Long savedId = objectMapper.readTree(
                        result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        HotelBooking saved = hotelBookingRepository.findByIdWithDetails(savedId)
                .orElseThrow();
        assertEquals(HotelBookingStatus.CONFIRMED_SIMULATED, saved.getStatus());
        assertTrue(saved.getHotelBookingReference().startsWith("HOTEL-"));
        assertTrue(saved.getExternalBookingRef().startsWith("SIM-HOTEL-"));
        assertEquals(2, saved.getNumberOfNights());
        assertTrue(saved.getModifications().isEmpty());
    }

    @Test
    @Order(2)
    void createHotelBooking_Fails_WhenCheckOutNotAfterCheckIn() throws Exception {
        CreateHotelBookingRequest req = validHotel(userAJourney.getId(),
                NEXT_WEEK, NEXT_WEEK);

        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.HOTEL_INVALID_DATES.name()));

        assertEquals(0, hotelBookingRepository.count());
    }

    @Test
    @Order(3)
    void createHotelBooking_Fails_WhenJourneyNotOwned() throws Exception {
        CreateHotelBookingRequest req = validHotel(userBJourney.getId(),
                NEXT_WEEK, NEXT_WEEK.plusDays(1));

        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        assertEquals(0, hotelBookingRepository.count());
    }

    @Test
    @Order(4)
    void createHotelBooking_Fails_WhenJourneyAbsent() throws Exception {
        CreateHotelBookingRequest req = validHotel(999_999L,
                NEXT_WEEK, NEXT_WEEK.plusDays(1));

        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.RESOURCE_NOT_FOUND.name()));

        assertEquals(0, hotelBookingRepository.count());
    }

    @Test
    @Order(5)
    void getHotelsForJourney_Ownership_Works() throws Exception {
        CreateHotelBookingRequest reqA = validHotel(userAJourney.getId(),
                NEXT_WEEK, NEXT_WEEK.plusDays(1));
        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(HOTELS_BASE + "/journey/" + userAJourney.getId())
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hotelName").value("The Gateway"));

        mockMvc.perform(get(HOTELS_BASE + "/journey/" + userAJourney.getId())
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        mockMvc.perform(get(HOTELS_BASE + "/journey/999999")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(6)
    void rescheduleHotel_Success_AuditRecorded() throws Exception {
        LocalDate oldCheckIn = NEXT_WEEK;
        LocalDate oldCheckOut = NEXT_WEEK.plusDays(2);
        LocalDate newCheckIn = IN_2_WEEKS;
        LocalDate newCheckOut = IN_2_WEEKS.plusDays(3);

        MvcResult created = mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validHotel(userAJourney.getId(), oldCheckIn, oldCheckOut))))
                .andExpect(status().isCreated())
                .andReturn();

        Long hotelId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        RescheduleHotelRequest rescheduleReq = RescheduleHotelRequest.builder()
                .newCheckInDate(newCheckIn)
                .newCheckOutDate(newCheckOut)
                .build();

        mockMvc.perform(patch(HOTELS_BASE + "/" + hotelId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rescheduleReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED_SIMULATED"))
                .andExpect(jsonPath("$.data.checkInDate").value(newCheckIn.toString()))
                .andExpect(jsonPath("$.data.checkOutDate").value(newCheckOut.toString()))
                .andExpect(jsonPath("$.data.numberOfNights").value(3))
                .andExpect(jsonPath("$.data.rescheduledAt").exists())
                .andExpect(jsonPath("$.data.modifications.length()").value(1))
                .andExpect(jsonPath("$.data.modifications[0].oldCheckInDate")
                        .value(oldCheckIn.toString()))
                .andExpect(jsonPath("$.data.modifications[0].newCheckInDate")
                        .value(newCheckIn.toString()))
                .andExpect(jsonPath("$.data.modifications[0].oldCheckOutDate")
                        .value(oldCheckOut.toString()))
                .andExpect(jsonPath("$.data.modifications[0].newCheckOutDate")
                        .value(newCheckOut.toString()))
                .andExpect(jsonPath("$.data.modifications[0].rescheduledAt").exists())
                .andExpect(jsonPath("$.data.modifications[0].simulationProvider")
                        .value("MOCK"));

        HotelBooking updated = hotelBookingRepository.findByIdWithDetails(hotelId).orElseThrow();
        assertEquals(HotelBookingStatus.RESCHEDULED_SIMULATED, updated.getStatus());
        assertEquals(newCheckIn, updated.getCheckInDate());
        assertEquals(newCheckOut, updated.getCheckOutDate());
        assertEquals(3, updated.getNumberOfNights());
        assertEquals(1, updated.getModifications().size());
        assertEquals(1, auditRepository.count());
    }

    @Test
    @Order(7)
    void rescheduleHotel_Fails_InvalidDates() throws Exception {
        MvcResult created = mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validHotel(userAJourney.getId(), NEXT_WEEK, NEXT_WEEK.plusDays(2)))))
                .andExpect(status().isCreated())
                .andReturn();

        Long hotelId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        HotelBooking before = hotelBookingRepository.findByIdWithDetails(hotelId).orElseThrow();
        assertEquals(2, before.getNumberOfNights());

        RescheduleHotelRequest invalid = RescheduleHotelRequest.builder()
                .newCheckInDate(IN_2_WEEKS)
                .newCheckOutDate(IN_2_WEEKS)
                .build();

        mockMvc.perform(patch(HOTELS_BASE + "/" + hotelId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.HOTEL_INVALID_DATES.name()));

        HotelBooking unchanged = hotelBookingRepository.findByIdWithDetails(hotelId).orElseThrow();
        assertEquals(NEXT_WEEK, unchanged.getCheckInDate());
        assertEquals(NEXT_WEEK.plusDays(2), unchanged.getCheckOutDate());
        assertEquals(2, unchanged.getNumberOfNights());
        assertEquals(HotelBookingStatus.CONFIRMED_SIMULATED, unchanged.getStatus());
        assertEquals(0, auditRepository.count());
    }

    @Test
    @Order(8)
    void rescheduleHotel_Fails_CrossUser() throws Exception {
        MvcResult created = mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validHotel(userAJourney.getId(), NEXT_WEEK, NEXT_WEEK.plusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn();

        Long hotelId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        RescheduleHotelRequest req = RescheduleHotelRequest.builder()
                .newCheckInDate(IN_2_WEEKS)
                .newCheckOutDate(IN_2_WEEKS.plusDays(1))
                .build();

        mockMvc.perform(patch(HOTELS_BASE + "/" + hotelId + "/reschedule")
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        HotelBooking unchanged = hotelBookingRepository.findByIdWithDetails(hotelId).orElseThrow();
        assertEquals(NEXT_WEEK, unchanged.getCheckInDate());
        assertEquals(HotelBookingStatus.CONFIRMED_SIMULATED, unchanged.getStatus());
        assertEquals(0, auditRepository.count());
    }

    @Test
    @Order(9)
    void createHotelBooking_Fails_Unauthenticated_And_ValidationErrors() throws Exception {
        CreateHotelBookingRequest req = validHotel(userAJourney.getId(),
                NEXT_WEEK, NEXT_WEEK.plusDays(1));

        mockMvc.perform(post(HOTELS_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());

        CreateHotelBookingRequest nullJourney = CreateHotelBookingRequest.builder()
                .journeyId(null)
                .hotelName("X")
                .city("X")
                .hotelAddress("Y")
                .checkInDate(NEXT_WEEK)
                .checkOutDate(NEXT_WEEK.plusDays(1))
                .build();
        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullJourney)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.journeyId").exists());

        CreateHotelBookingRequest blankFields = CreateHotelBookingRequest.builder()
                .journeyId(userAJourney.getId())
                .hotelName("")
                .city("")
                .hotelAddress("")
                .checkInDate(NEXT_WEEK)
                .checkOutDate(NEXT_WEEK.plusDays(1))
                .build();
        mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blankFields)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.hotelName").exists())
                .andExpect(jsonPath("$.validationErrors.city").exists())
                .andExpect(jsonPath("$.validationErrors.hotelAddress").exists());
    }

    @Test
    @Order(10)
    void createHotelBooking_StatusNeverStoresPlainConfirmed_AndRescheduleNoDatesIsError() throws Exception {
        MvcResult created = mockMvc.perform(post(HOTELS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validHotel(userAJourney.getId(), NEXT_WEEK, NEXT_WEEK.plusDays(1)))))
                .andExpect(status().isCreated())
                .andReturn();

        Long hotelId = objectMapper.readTree(created.getResponse()
                .getContentAsString()).path("data").path("id").asLong();

        List<HotelBooking> all = hotelBookingRepository.findAll();
        for (HotelBooking hb : all) {
            assertNotEquals("CONFIRMED", hb.getStatus().name());
            assertNotEquals("RESCHEDULED", hb.getStatus().name());
            assertTrue(hb.getStatus().name().endsWith("_SIMULATED"));
        }

        mockMvc.perform(patch(HOTELS_BASE + "/" + hotelId + "/reschedule")
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RescheduleHotelRequest.builder().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode")
                        .value(ErrorCode.HOTEL_RESCHEDULE_NO_CHANGES.name()));
    }
}
