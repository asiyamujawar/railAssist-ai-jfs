package com.trainconcierge.booking;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.booking.dto.BookTrainRequest;
import com.trainconcierge.booking.dto.BookingResponse;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.exception.ErrorCode;
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
class BookingJourneyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainScheduleRepository trainScheduleRepository;

    @Autowired
    private SeatAvailabilityRepository seatAvailabilityRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final String BOOKINGS_BASE = "/api/bookings";
    private static final String MY_BOOKINGS = "/api/bookings/my";

    private static final String USER_A_EMAIL = "user.a.booking@example.com";
    private static final String USER_B_EMAIL = "user.b.booking@example.com";
    private static final String ADMIN_EMAIL = "admin.booking@example.com";
    private static final String TEST_PASSWORD = "Booking@2026";

    private String userAToken;
    private String userBToken;
    private String adminToken;

    private Train trainA;
    private TrainSchedule schedule_today;
    private TrainSchedule schedule_tomorrow;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    private static final BigDecimal SECOND_FARE = new BigDecimal("450.00");
    private static final BigDecimal BUSINESS_FARE = new BigDecimal("950.00");

    @BeforeEach
    void setUp() {
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
        adminToken = createUserAndGetToken(ADMIN_EMAIL, UserRole.ROLE_ADMIN);

        trainA = createAndSaveTrain("BK001", "Booking Express",
                "Chennai Central", "Bengaluru City", 100, true);

        schedule_today = createAndSaveSchedule(trainA, TODAY,
                LocalTime.of(7, 30), LocalTime.of(11, 45));
        schedule_tomorrow = createAndSaveSchedule(trainA, TOMORROW,
                LocalTime.of(19, 0), LocalTime.of(23, 15));

        seedDeterministicSeats(schedule_today);
        seedDeterministicSeats(schedule_tomorrow);
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1. POST /api/bookings — happy path
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("POST /api/bookings — 201 CREATED: booking persists, seats decrement, journey created")
    void createBooking_Success_DecrementsInventory_AndCreatesJourney() throws Exception {
        SeatAvailability before = getSeatAvailability(schedule_today, SeatClass.SECOND);
        int availBefore = before.getAvailableSeats();
        int bookedBefore = before.getBookedSeats();
        assertEquals(5, availBefore, "Test precondition: SECOND class has exactly 5 available seats");

        int passengers = 2;
        BookTrainRequest req = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(passengers)
                .currency("INR")
                .build();

        MvcResult result = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("booking created")))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.bookingReference", startsWith("TC-")))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.passengerName").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduleId").value(schedule_today.getId()))
                .andExpect(jsonPath("$.data.trainId").value(trainA.getId()))
                .andExpect(jsonPath("$.data.trainNumber").value("BK001"))
                .andExpect(jsonPath("$.data.trainName").value("Booking Express"))
                .andExpect(jsonPath("$.data.originStation").value("Chennai Central"))
                .andExpect(jsonPath("$.data.destinationStation").value("Bengaluru City"))
                .andExpect(jsonPath("$.data.journeyDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.scheduledDeparture").value("07:30:00"))
                .andExpect(jsonPath("$.data.scheduledArrival").value("11:45:00"))
                .andExpect(jsonPath("$.data.platform").value("1"))
                .andExpect(jsonPath("$.data.seatClass").value(SeatClass.SECOND.name()))
                .andExpect(jsonPath("$.data.numberOfSeats").value(passengers))
                .andExpect(jsonPath("$.data.totalFare",
                        is(closeTo(SECOND_FARE.multiply(BigDecimal.valueOf(passengers)).doubleValue(), 0.001))))
                .andExpect(jsonPath("$.data.baseFare").isNumber())
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andExpect(jsonPath("$.data.status").value(BookingStatus.CONFIRMED.name()))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancelledAt").isEmpty())
                .andExpect(jsonPath("$.data.journey.id").isNumber())
                .andExpect(jsonPath("$.data.journey.originStation").value("Chennai Central"))
                .andExpect(jsonPath("$.data.journey.destinationStation").value("Bengaluru City"))
                .andExpect(jsonPath("$.data.journey.travelDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.journey.status").value(JourneyStatus.PLANNED.name()))
                .andExpect(jsonPath("$.data.journey.totalCost",
                        is(closeTo(SECOND_FARE.multiply(BigDecimal.valueOf(passengers)).doubleValue(), 0.001))))
                .andExpect(jsonPath("$.data.journey.currency").value("INR"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andReturn();

        BookingResponse parsed = objectMapper.treeToValue(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data"),
                BookingResponse.class);
        Long bookingId = parsed.getId();
        Long journeyId = parsed.getJourney().getId();

        SeatAvailability after = getSeatAvailability(schedule_today, SeatClass.SECOND);
        assertEquals(availBefore - passengers, after.getAvailableSeats(),
                "Available seats should have decremented by passenger count");
        assertEquals(bookedBefore + passengers, after.getBookedSeats(),
                "Booked seats should have incremented by passenger count");
        assertEquals(before.getTotalSeats(), after.getTotalSeats());

        Booking dbBooking = bookingRepository.findByIdWithDetails(bookingId).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, dbBooking.getStatus());
        assertEquals(parsed.getBookingReference(), dbBooking.getBookingReference());
        assertEquals(USER_A_EMAIL.toLowerCase(), dbBooking.getUser().getEmail());
        assertEquals(SeatClass.SECOND, dbBooking.getSeatClass());
        assertEquals(passengers, dbBooking.getNumberOfSeats());
        assertEquals(0, SECOND_FARE.multiply(BigDecimal.valueOf(passengers))
                .compareTo(dbBooking.getTotalFare()));

        User userA = userRepository.findByEmail(USER_A_EMAIL.toLowerCase()).orElseThrow();
        Journey dbJourney = journeyRepository.findById(journeyId).orElseThrow();
        assertEquals(JourneyStatus.PLANNED, dbJourney.getStatus());
        List<Journey> userAJourneys = journeyRepository.findByUser(userA);
        assertTrue(userAJourneys.stream().anyMatch(j -> j.getId().equals(journeyId)),
                "Journey must be linked to user A via findByUser reverse-lookup");
        assertEquals("Chennai Central", dbJourney.getOriginStation());
        assertEquals("Bengaluru City", dbJourney.getDestinationStation());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2. POST /api/bookings — insufficient seats
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("POST /api/bookings — 422: seats requested exceed available (5 avail, 6 requested)")
    void createBooking_Fails_WhenInsufficientSeats() throws Exception {
        SeatAvailability before = getSeatAvailability(schedule_today, SeatClass.SECOND);
        assertEquals(5, before.getAvailableSeats());

        BookTrainRequest req = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(6)
                .build();

        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INSUFFICIENT_SEATS.name()))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("insufficient")));

        SeatAvailability after = getSeatAvailability(schedule_today, SeatClass.SECOND);
        assertEquals(before.getAvailableSeats(), after.getAvailableSeats(),
                "Inventory must be unchanged on insufficient seats");
        assertEquals(before.getBookedSeats(), after.getBookedSeats());

        List<Booking> allBookings = bookingRepository.findAll();
        assertTrue(allBookings.isEmpty(), "No booking rows must be created on failure");
        List<Journey> allJourneys = journeyRepository.findAll();
        assertTrue(allJourneys.isEmpty(), "No journey rows must be created on failure");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3. POST /api/bookings — active duplicate rejected
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @DisplayName("POST /api/bookings — 409 CONFLICT: same user+schedule+class while another booking active")
    void createBooking_Fails_WhenActiveDuplicateExists() throws Exception {
        BookTrainRequest req = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();

        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_ALREADY_EXISTS.name()));

        long count = bookingRepository.count();
        assertEquals(1L, count, "Only the first booking row should exist");
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4. PATCH /api/bookings/{id}/cancel — happy path
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("PATCH /api/bookings/{id}/cancel — 200: seats restored, booking CANCELLED, journey CANCELLED (history preserved)")
    void cancelBooking_Success_RestoresSeats_MarksCancelled() throws Exception {
        int passengers = 3;
        BookTrainRequest req = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.BUSINESS)
                .passengerCount(passengers)
                .build();

        MvcResult createResult = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        Long bookingId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        SeatAvailability justAfter = getSeatAvailability(schedule_today, SeatClass.BUSINESS);
        int availAfterBooking = justAfter.getAvailableSeats();
        int bookedAfterBooking = justAfter.getBookedSeats();

        MvcResult cancelResult = mockMvc.perform(patch(BOOKINGS_BASE + "/" + bookingId + "/cancel")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("cancelled")))
                .andExpect(jsonPath("$.data.id").value(bookingId))
                .andExpect(jsonPath("$.data.status").value(BookingStatus.CANCELLED.name()))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.journey.status").value(JourneyStatus.CANCELLED.name()))
                .andReturn();

        Long journeyId = objectMapper.readTree(cancelResult.getResponse().getContentAsString())
                .path("data").path("journey").path("id").asLong();

        SeatAvailability afterCancel = getSeatAvailability(schedule_today, SeatClass.BUSINESS);
        assertEquals(availAfterBooking + passengers, afterCancel.getAvailableSeats(),
                "Cancelled seats should be returned to inventory");
        assertEquals(bookedAfterBooking - passengers, afterCancel.getBookedSeats());

        assertTrue(bookingRepository.findById(bookingId).isPresent(),
                "Booking record must still exist (history, not deleted)");
        Booking dbBooking = bookingRepository.findById(bookingId).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, dbBooking.getStatus());
        assertNotNull(dbBooking.getCancelledAt());
        assertEquals(passengers, dbBooking.getNumberOfSeats());

        assertTrue(journeyRepository.findById(journeyId).isPresent(),
                "Journey record must still exist (history)");
        Journey dbJourney = journeyRepository.findById(journeyId).orElseThrow();
        assertEquals(JourneyStatus.CANCELLED, dbJourney.getStatus());

        mockMvc.perform(get(BOOKINGS_BASE + "/" + bookingId)
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(BookingStatus.CANCELLED.name()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5. PATCH cancel — already cancelled / COMPLETED (not cancellable)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("PATCH cancel — 422 re-cancel (BOOKING_ALREADY_CANCELLED) and COMPLETED cannot cancel (BOOKING_NOT_CANCELLABLE)")
    void cancelBooking_Fails_AlreadyCancelled_And_NotCancellable() throws Exception {
        BookTrainRequest reqA = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        MvcResult rA = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated()).andReturn();
        Long idA = objectMapper.readTree(rA.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(patch(BOOKINGS_BASE + "/" + idA + "/cancel")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk());

        mockMvc.perform(patch(BOOKINGS_BASE + "/" + idA + "/cancel")
                        .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.BOOKING_ALREADY_CANCELLED.name()));

        BookTrainRequest reqB = BookTrainRequest.builder()
                .scheduleId(schedule_tomorrow.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        MvcResult rB = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userBToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated()).andReturn();
        Long idB = objectMapper.readTree(rB.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Booking bookingB = bookingRepository.findById(idB).orElseThrow();
        bookingB.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(bookingB);

        mockMvc.perform(patch(BOOKINGS_BASE + "/" + idB + "/cancel")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.BOOKING_NOT_CANCELLABLE.name()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6. Ownership — cross-user access forbidden
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(6)
    @DisplayName("GET and PATCH /api/bookings/{id} — 403 FORBIDDEN when caller is not owner")
    void getAndCancelBooking_Fail_WhenNotOwner() throws Exception {
        BookTrainRequest req = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(2)
                .build();
        MvcResult r = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        Long id = objectMapper.readTree(r.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get(BOOKINGS_BASE + "/" + id)
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        mockMvc.perform(patch(BOOKINGS_BASE + "/" + id + "/cancel")
                        .header("Authorization", "Bearer " + userBToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));

        mockMvc.perform(get(MY_BOOKINGS)
                        .header("Authorization", "Bearer " + userAToken)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get(MY_BOOKINGS)
                        .header("Authorization", "Bearer " + userBToken)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7. GET /api/bookings/my — pagination + sorting
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("GET /api/bookings/my — paginated desc by createdAt (3 bookings, page=0 size=2)")
    void getMyBookings_Pagination_Sorting_Works() throws Exception {
        BookTrainRequest req1 = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1).build();
        BookTrainRequest req2 = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.BUSINESS)
                .passengerCount(1).build();
        BookTrainRequest req3 = BookTrainRequest.builder()
                .scheduleId(schedule_tomorrow.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1).build();

        Thread.sleep(50);
        MvcResult r1 = mockMvc.perform(post(BOOKINGS_BASE)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1))).andReturn();
        Long id1 = objectMapper.readTree(r1.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Thread.sleep(50);
        MvcResult r2 = mockMvc.perform(post(BOOKINGS_BASE)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2))).andReturn();
        Long id2 = objectMapper.readTree(r2.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        Thread.sleep(50);
        MvcResult r3 = mockMvc.perform(post(BOOKINGS_BASE)
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req3))).andReturn();
        Long id3 = objectMapper.readTree(r3.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get(MY_BOOKINGS)
                        .header("Authorization", "Bearer " + userAToken)
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.empty").value(false))
                .andExpect(jsonPath("$.data.content[0].id").value(id3))
                .andExpect(jsonPath("$.data.content[1].id").value(id2));

        mockMvc.perform(get(MY_BOOKINGS)
                        .header("Authorization", "Bearer " + userAToken)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortBy", "createdAt")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.data.content[0].id").value(id1));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 8. Validation + unauthenticated
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("POST /api/bookings — 400 validation on bad inputs, 401 without token, 404 missing schedule")
    void createBooking_Fails_Validation_And_Unauthenticated_And_NotFound() throws Exception {
        mockMvc.perform(post(BOOKINGS_BASE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        BookTrainRequest noSchedule = BookTrainRequest.builder()
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSchedule)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.scheduleId",
                        anyOf(containsStringIgnoringCase("required"), containsStringIgnoringCase("null"))));

        BookTrainRequest zeroPassengers = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(0)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zeroPassengers)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.passengerCount",
                        anyOf(containsStringIgnoringCase("at least 1"), containsStringIgnoringCase("1"))));

        BookTrainRequest tooMany = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(10)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tooMany)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.passengerCount",
                        containsStringIgnoringCase("9")));

        BookTrainRequest nonexistentSchedule = BookTrainRequest.builder()
                .scheduleId(99_999L)
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonexistentSchedule)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));

        BookTrainRequest noSeatClass = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .passengerCount(1)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noSeatClass)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.seatClass",
                        anyOf(containsStringIgnoringCase("required"), containsStringIgnoringCase("null"))));

        BookTrainRequest nullPassengers = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullPassengers)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.passengerCount",
                        anyOf(containsStringIgnoringCase("required"), containsStringIgnoringCase("null"))));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 9. Fare math + booking reference uniqueness
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @Order(9)
    @DisplayName("POST /api/bookings — totalFare = per-seat × passengers; references are unique; default currency INR")
    void createBooking_FareReferenceUniqueness_AndDefaultCurrency() throws Exception {
        BookTrainRequest reqA = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.BUSINESS)
                .passengerCount(3)
                .build();

        MvcResult rA = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalFare",
                        is(closeTo(BUSINESS_FARE.multiply(BigDecimal.valueOf(3)).doubleValue(), 0.001))))
                .andExpect(jsonPath("$.data.currency").value("INR"))
                .andReturn();
        String refA = objectMapper.readTree(rA.getResponse().getContentAsString())
                .path("data").path("bookingReference").asText();
        assertTrue(refA.startsWith("TC-"), refA + " should start with TC-");

        BookTrainRequest reqB = BookTrainRequest.builder()
                .scheduleId(schedule_tomorrow.getId())
                .seatClass(SeatClass.BUSINESS)
                .passengerCount(1)
                .build();
        MvcResult rB = mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqB)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalFare", is(closeTo(BUSINESS_FARE.doubleValue(), 0.001))))
                .andReturn();
        String refB = objectMapper.readTree(rB.getResponse().getContentAsString())
                .path("data").path("bookingReference").asText();

        assertNotEquals(refA, refB, "Two bookings must have distinct references");
        assertEquals(2L, bookingRepository.count());
    }

    @Test
    @Order(10)
    @DisplayName("POST /api/bookings — 422 on cancelled schedule (SCHEDULE_CANCELLED) and inactive train (INVALID_OPERATION)")
    void createBooking_Fails_WhenScheduleCancelled_OrTrainInactive() throws Exception {
        schedule_today.setCancelled(true);
        trainScheduleRepository.save(schedule_today);

        BookTrainRequest req1 = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.SCHEDULE_CANCELLED.name()));

        schedule_today.setCancelled(false);
        trainScheduleRepository.save(schedule_today);
        trainA.setActive(false);
        trainRepository.save(trainA);

        BookTrainRequest req2 = BookTrainRequest.builder()
                .scheduleId(schedule_today.getId())
                .seatClass(SeatClass.SECOND)
                .passengerCount(1)
                .build();
        mockMvc.perform(post(BOOKINGS_BASE)
                        .header("Authorization", "Bearer " + userAToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()));
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private SeatAvailability getSeatAvailability(TrainSchedule schedule, SeatClass cls) {
        return seatAvailabilityRepository.findByScheduleAndSeatClass(schedule, cls)
                .orElseThrow(() -> new AssertionError("SeatAvailability not seeded: " + cls));
    }

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName(role == UserRole.ROLE_ADMIN ? "Admin" : "User")
                .lastName("BookTester")
                .email(email.toLowerCase())
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .enabled(true)
                .build();
        userRepository.save(user);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .subject(email.toLowerCase())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    private Train createAndSaveTrain(String number, String name,
                                     String origin, String destination,
                                     int seats, boolean active) {
        Train t = Train.builder()
                .trainNumber(number)
                .trainName(name)
                .operatorName("TestRail")
                .originStation(origin)
                .destinationStation(destination)
                .totalSeats(seats)
                .active(active)
                .build();
        return trainRepository.save(t);
    }

    private TrainSchedule createAndSaveSchedule(Train train, LocalDate date,
                                                LocalTime dep, LocalTime arr) {
        TrainSchedule s = TrainSchedule.builder()
                .train(train)
                .scheduledDate(date)
                .scheduledDeparture(dep)
                .scheduledArrival(arr)
                .delayMinutes(0)
                .platform("1")
                .cancelled(false)
                .scheduleStatus(com.trainconcierge.schedule.ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(500.00))
                .build();
        return trainScheduleRepository.save(s);
    }

    private void seedDeterministicSeats(TrainSchedule schedule) {
        List<SeatAvailability> list = List.of(
                SeatAvailability.builder()
                        .schedule(schedule).seatClass(SeatClass.FIRST)
                        .totalSeats(10).availableSeats(2).bookedSeats(8)
                        .fare(new BigDecimal("1500.00")).build(),
                SeatAvailability.builder()
                        .schedule(schedule).seatClass(SeatClass.BUSINESS)
                        .totalSeats(20).availableSeats(10).bookedSeats(10)
                        .fare(BUSINESS_FARE).build(),
                SeatAvailability.builder()
                        .schedule(schedule).seatClass(SeatClass.SECOND)
                        .totalSeats(5).availableSeats(5).bookedSeats(0)
                        .fare(SECOND_FARE).build(),
                SeatAvailability.builder()
                        .schedule(schedule).seatClass(SeatClass.SLEEPER)
                        .totalSeats(30).availableSeats(15).bookedSeats(15)
                        .fare(new BigDecimal("720.00")).build()
        );
        seatAvailabilityRepository.saveAll(list);
    }
}
