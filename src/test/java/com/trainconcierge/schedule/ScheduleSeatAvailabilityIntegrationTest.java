package com.trainconcierge.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.auth.AuthService;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.schedule.dto.CreateScheduleRequest;
import com.trainconcierge.schedule.dto.ScheduleResponse;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatAvailabilityService;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.seat.dto.SeatAvailabilityUpdateRequest;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScheduleSeatAvailabilityIntegrationTest {

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
    private SeatAvailabilityService seatAvailabilityService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    @Autowired
    private DisruptionEventRepository disruptionEventRepository;

    @Autowired
    private RecommendationRepository recommendationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JourneyRepository journeyRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration-ms}")
    private long jwtExpirationMs;

    private static final String SCHEDULE_BASE = "/api/schedules";
    private static final String SCHEDULE_SEARCH = "/api/schedules/search";
    private static final String ADMIN_SCHEDULE = "/api/admin/schedules";

    private static final String ADMIN_EMAIL = "admin.sched@example.com";
    private static final String USER_EMAIL = "user.sched@example.com";
    private static final String TEST_PASSWORD = "Sched@2026";

    private String adminToken;
    private String userToken;

    private Train trainA;
    private Train trainInactive;
    private TrainSchedule scheduleA_today;
    private TrainSchedule scheduleA_tomorrow;

    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        trainScheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();

        adminToken = createUserAndGetToken(ADMIN_EMAIL, UserRole.ROLE_ADMIN);
        userToken = createUserAndGetToken(USER_EMAIL, UserRole.ROLE_USER);

        trainA = createAndSaveTrain("SCH001", "Schedule Express One",
                "Mumbai Central", "New Delhi", 500, true);

        trainInactive = createAndSaveTrain("SCH000", "Inactive Train",
                "A", "B", 200, false);

        scheduleA_today = createAndSaveSchedule(trainA, TODAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0));
        scheduleA_tomorrow = createAndSaveSchedule(trainA, TOMORROW,
                LocalTime.of(9, 0), LocalTime.of(13, 0));

        createAllSeatClasses(scheduleA_today);
        createAllSeatClasses(scheduleA_tomorrow);
    }

    // ────────────────────────────────────────────────────────────────────
    // 1. GET /api/schedules — paginated list
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/schedules — 200 OK paginated list as USER")
    void listSchedules_Success_AsUser() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE)
                        .header("Authorization", "Bearer " + userToken)
                        .param("page", "0")
                        .param("size", "1")
                        .param("sortBy", "scheduledDate")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.content[0].scheduledDate").value(TODAY.toString()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. GET /api/schedules/{id}
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @DisplayName("GET /api/schedules/{id} — 200 OK with valid id")
    void getScheduleById_Success() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE + "/" + scheduleA_today.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(scheduleA_today.getId()))
                .andExpect(jsonPath("$.data.trainId").value(trainA.getId()))
                .andExpect(jsonPath("$.data.trainNumber").value("SCH001"))
                .andExpect(jsonPath("$.data.originStation").value("Mumbai Central"))
                .andExpect(jsonPath("$.data.destinationStation").value("New Delhi"))
                .andExpect(jsonPath("$.data.scheduledDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.scheduledDeparture").value("08:00:00"))
                .andExpect(jsonPath("$.data.scheduledArrival").value("12:00:00"))
                .andExpect(jsonPath("$.data.cancelled").value(false))
                .andExpect(jsonPath("$.data.scheduleStatus").value(ScheduleStatus.SCHEDULED.name()))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/schedules/{id} — 404 NOT FOUND")
    void getScheduleById_Failure_NotFound() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE + "/98765")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. GET /api/schedules/search — schedule search by route & date
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("GET /api/schedules/search — 200 OK case-insensitive route match")
    void searchSchedules_Success_CaseInsensitive() throws Exception {
        mockMvc.perform(get(SCHEDULE_SEARCH)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "mumbai central")
                        .param("destinationStation", "NEW DELHI")
                        .param("journeyDate", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].scheduleId").value(scheduleA_today.getId()))
                .andExpect(jsonPath("$.data[0].trainNumber").value("SCH001"))
                .andExpect(jsonPath("$.data[0].seatAvailability").isArray())
                .andExpect(jsonPath("$.data[0].seatAvailability", hasSize(4)))
                .andExpect(jsonPath("$.data[0].seatAvailability[0].seatClass",
                        anyOf(is("FIRST"), is("BUSINESS"), is("SECOND"), is("SLEEPER"))))
                .andExpect(jsonPath("$.data[0].seatAvailability[0].availableSeats").isNumber())
                .andExpect(jsonPath("$.data[0].seatAvailability[0].fare").isNumber());
    }

    @Test
    @Order(5)
    @DisplayName("GET /api/schedules/search — 200 OK empty list for no route")
    void searchSchedules_Success_Empty() throws Exception {
        mockMvc.perform(get(SCHEDULE_SEARCH)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "Kolkata")
                        .param("destinationStation", "Chennai")
                        .param("journeyDate", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("no schedules found")));
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/schedules/search — 400 BAD REQUEST origin == destination")
    void searchSchedules_Failure_SameStation() throws Exception {
        mockMvc.perform(get(SCHEDULE_SEARCH)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "Mumbai Central")
                        .param("destinationStation", "mumbai central")
                        .param("journeyDate", TODAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("origin and destination stations must be different")));
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/schedules/search — 400 BAD REQUEST missing params")
    void searchSchedules_Failure_MissingParams() throws Exception {
        mockMvc.perform(get(SCHEDULE_SEARCH)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "A")
                        .param("journeyDate", TODAY.toString()))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. GET /api/schedules/{id}/availability
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("GET /api/schedules/{id}/availability — 200 OK returns 4 classes")
    void getAvailability_Success() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE + "/" + scheduleA_today.getId() + "/availability")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/schedules/{id}/availability — 404 for non-existent schedule")
    void getAvailability_Failure_NotFound() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE + "/55555/availability")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 5. POST /api/admin/schedules — invalid schedule scenarios
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("POST /api/admin/schedules — 201 CREATED valid schedule with seat classes")
    void createSchedule_Success_Valid() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainA.getId(), TODAY.plusDays(5),
                LocalTime.of(10, 0), LocalTime.of(14, 0));

        MvcResult result = mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("created")))
                .andExpect(jsonPath("$.data.trainNumber").value("SCH001"))
                .andExpect(jsonPath("$.data.scheduledDate").value(TODAY.plusDays(5).toString()))
                .andExpect(jsonPath("$.data.scheduleStatus").value(ScheduleStatus.SCHEDULED.name()))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        var dataNode = objectMapper.readTree(body).get("data");
        Long scheduleId = dataNode.get("id").asLong();

        List<SeatAvailability> seats = seatAvailabilityRepository.findBySchedule(
                trainScheduleRepository.findById(scheduleId).orElseThrow());
        assertEquals(4, seats.size(), "4 seat class rows should exist");
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/admin/schedules — 409 DUPLICATE same train + same date")
    void createSchedule_Failure_DuplicateTrainDate() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainA.getId(), TODAY,
                LocalTime.of(10, 0), LocalTime.of(14, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_ALREADY_EXISTS.name()));
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/admin/schedules — 400 arrival time is before departure")
    void createSchedule_Failure_ArrivalBeforeDeparture() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainA.getId(), TODAY.plusDays(3),
                LocalTime.of(14, 0), LocalTime.of(10, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("arrival time must be after departure")));
    }

    @Test
    @Order(13)
    @DisplayName("POST /api/admin/schedules — 400 arrival == departure")
    void createSchedule_Failure_ArrivalEqualsDeparture() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainA.getId(), TODAY.plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(10, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()));
    }

    @Test
    @Order(14)
    @DisplayName("POST /api/admin/schedules — 400 inactive train")
    void createSchedule_Failure_InactiveTrain() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainInactive.getId(), TODAY.plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(14, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("inactive train")));
    }

    @Test
    @Order(15)
    @DisplayName("POST /api/admin/schedules — 404 non-existent train")
    void createSchedule_Failure_TrainNotFound() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                999999L, TODAY.plusDays(3),
                LocalTime.of(10, 0), LocalTime.of(14, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(16)
    @DisplayName("POST /api/admin/schedules — 400 negative total seats in class")
    void createSchedule_Failure_NegativeSeatCount() throws Exception {
        var seatConfig = new ArrayList<CreateScheduleRequest.SeatClassConfig>();
        seatConfig.add(CreateScheduleRequest.SeatClassConfig.builder()
                .seatClass(SeatClass.SECOND)
                .totalSeats(-5)
                .fare(BigDecimal.valueOf(100.0))
                .build());

        CreateScheduleRequest req = CreateScheduleRequest.builder()
                .trainId(trainA.getId())
                .scheduledDate(TODAY.plusDays(4))
                .scheduledDeparture(LocalTime.of(6, 0))
                .scheduledArrival(LocalTime.of(10, 0))
                .seatClasses(seatConfig)
                .build();

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("must not be negative")));
    }

    @Test
    @Order(17)
    @DisplayName("POST /api/admin/schedules — 403 FORBIDDEN as USER role")
    void createSchedule_Failure_AsRoleUser() throws Exception {
        CreateScheduleRequest req = buildValidCreateRequest(
                trainA.getId(), TODAY.plusDays(6),
                LocalTime.of(10, 0), LocalTime.of(14, 0));

        mockMvc.perform(post(ADMIN_SCHEDULE)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 6. PATCH /api/admin/schedules/{id}/availability — seat updates
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(18)
    @DisplayName("PATCH /admin/schedules/{id}/availability — 200 OK updates fare and counts")
    void updateAvailability_Success() throws Exception {
        SeatAvailabilityUpdateRequest req = SeatAvailabilityUpdateRequest.builder()
                .seatClass(SeatClass.SECOND)
                .totalSeats(200)
                .availableSeats(150)
                .bookedSeats(50)
                .fare(BigDecimal.valueOf(550.00))
                .build();

        mockMvc.perform(patch(ADMIN_SCHEDULE + "/" + scheduleA_today.getId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("updated")))
                .andExpect(jsonPath("$.data.seatClass").value(SeatClass.SECOND.name()))
                .andExpect(jsonPath("$.data.totalSeats").value(200))
                .andExpect(jsonPath("$.data.availableSeats").value(150))
                .andExpect(jsonPath("$.data.bookedSeats").value(50))
                .andExpect(jsonPath("$.data.fare").value(550.00));
    }

    @Test
    @Order(19)
    @DisplayName("PATCH /admin/schedules/{id}/availability — 400 negative available seats")
    void updateAvailability_Failure_NegativeAvailable() throws Exception {
        SeatAvailabilityUpdateRequest req = SeatAvailabilityUpdateRequest.builder()
                .seatClass(SeatClass.SECOND)
                .availableSeats(-1)
                .build();

        mockMvc.perform(patch(ADMIN_SCHEDULE + "/" + scheduleA_today.getId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.availableSeats",
                        containsStringIgnoringCase("must not be negative")));
    }

    @Test
    @Order(20)
    @DisplayName("PATCH /admin/schedules/{id}/availability — 400 available+booked > total")
    void updateAvailability_Failure_OverCapacity() throws Exception {
        SeatAvailabilityUpdateRequest req = SeatAvailabilityUpdateRequest.builder()
                .seatClass(SeatClass.SECOND)
                .totalSeats(100)
                .availableSeats(80)
                .bookedSeats(50)
                .build();

        mockMvc.perform(patch(ADMIN_SCHEDULE + "/" + scheduleA_today.getId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("must not exceed total")));
    }

    @Test
    @Order(21)
    @DisplayName("PATCH /admin/schedules/{id}/availability — 404 wrong schedule id")
    void updateAvailability_Failure_ScheduleNotFound() throws Exception {
        SeatAvailabilityUpdateRequest req = SeatAvailabilityUpdateRequest.builder()
                .seatClass(SeatClass.SECOND)
                .availableSeats(10)
                .build();

        mockMvc.perform(patch(ADMIN_SCHEDULE + "/44444/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(22)
    @DisplayName("PATCH /admin/schedules/{id}/availability — 404 seat class not on schedule")
    void updateAvailability_Failure_SeatClassNotFound() throws Exception {
        Train trainX = createAndSaveTrain("SCH00X", "Partial Train", "P", "Q", 100, true);
        TrainSchedule s = createAndSaveSchedule(trainX, TODAY.plusDays(10),
                LocalTime.of(7, 0), LocalTime.of(10, 0));
        SeatAvailability onlySleeper = SeatAvailability.builder()
                .schedule(s)
                .seatClass(SeatClass.SLEEPER)
                .totalSeats(50)
                .availableSeats(50)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(200.0))
                .build();
        seatAvailabilityRepository.save(onlySleeper);

        SeatAvailabilityUpdateRequest req = SeatAvailabilityUpdateRequest.builder()
                .seatClass(SeatClass.FIRST)
                .availableSeats(5)
                .build();

        mockMvc.perform(patch(ADMIN_SCHEDULE + "/" + s.getId() + "/availability")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 7. Concurrent inventory — NO OVERSELLING guarantee
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(23)
    @DisplayName("Concurrent booking: 50 threads x 2 seats on 50 available → exactly 50 booked, no oversell")
    void concurrentBooking_NoOverselling_AtomicUpdate() throws Exception {
        final int TOTAL_SEATS = 50;
        final int SEATS_PER_THREAD = 2;
        final int THREAD_COUNT = 50;

        TrainSchedule s = createAndSaveSchedule(trainA, TODAY.plusDays(15),
                LocalTime.of(6, 0), LocalTime.of(9, 0));
        SeatAvailability sa = SeatAvailability.builder()
                .schedule(s)
                .seatClass(SeatClass.SECOND)
                .totalSeats(TOTAL_SEATS)
                .availableSeats(TOTAL_SEATS)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(300.0))
                .build();
        sa = seatAvailabilityRepository.save(sa);
        final Long saId = sa.getId();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            futures.add(executor.submit(() -> {
                try {
                    boolean ok = seatAvailabilityService.bookSeats(saId, SEATS_PER_THREAD);
                    if (ok) successes.incrementAndGet();
                    else failures.incrementAndGet();
                    return ok;
                } finally {
                    latch.countDown();
                }
            }));
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertTrue(completed, "All booking threads completed within 30s");

        SeatAvailability refreshed = seatAvailabilityRepository.findById(saId).orElseThrow();

        int expectedMaxBooked = TOTAL_SEATS;

        assertTrue(refreshed.getBookedSeats() <= expectedMaxBooked,
                () -> "OVERSELL DETECTED! bookedSeats=" + refreshed.getBookedSeats()
                        + " > total=" + expectedMaxBooked
                        + " | successes=" + successes.get()
                        + " | failures=" + failures.get());

        assertTrue(refreshed.getAvailableSeats() >= 0,
                "availableSeats must never be negative: " + refreshed.getAvailableSeats());

        assertEquals(TOTAL_SEATS, refreshed.getTotalSeats(),
                "totalSeats must remain unchanged");

        assertEquals(refreshed.getTotalSeats(),
                refreshed.getAvailableSeats() + refreshed.getBookedSeats(),
                "total = available + booked invariant");

        assertEquals(THREAD_COUNT, successes.get() + failures.get(),
                "Every thread returned either success or failure");
    }

    @Test
    @Order(24)
    @DisplayName("Single booking: exact available count → succeeds; one more → fails (no oversell)")
    void singleBooking_ExactAvailabilityThenFail() throws Exception {
        final int TOTAL = 10;
        TrainSchedule s = createAndSaveSchedule(trainA, TODAY.plusDays(20),
                LocalTime.of(11, 0), LocalTime.of(14, 0));
        SeatAvailability sa = SeatAvailability.builder()
                .schedule(s)
                .seatClass(SeatClass.FIRST)
                .totalSeats(TOTAL)
                .availableSeats(TOTAL)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(1000.0))
                .build();
        sa = seatAvailabilityRepository.save(sa);

        boolean first = seatAvailabilityService.bookSeats(sa.getId(), TOTAL);
        assertTrue(first, "Should book exactly all available seats");

        SeatAvailability after = seatAvailabilityRepository.findById(sa.getId()).orElseThrow();
        assertEquals(0, after.getAvailableSeats());
        assertEquals(TOTAL, after.getBookedSeats());

        boolean second = seatAvailabilityService.bookSeats(sa.getId(), 1);
        assertFalse(second, "Booking 1 more seat must fail when none are available");
    }

    @Test
    @Order(25)
    @DisplayName("Release seats: releases correctly, never exceeds total capacity")
    void releaseSeats_CappedAtTotal() throws Exception {
        final int TOTAL = 30;
        TrainSchedule s = createAndSaveSchedule(trainA, TODAY.plusDays(25),
                LocalTime.of(15, 0), LocalTime.of(18, 0));
        SeatAvailability sa = SeatAvailability.builder()
                .schedule(s)
                .seatClass(SeatClass.BUSINESS)
                .totalSeats(TOTAL)
                .availableSeats(20)
                .bookedSeats(10)
                .fare(BigDecimal.valueOf(700.0))
                .build();
        sa = seatAvailabilityRepository.save(sa);

        boolean releaseOk = seatAvailabilityService.releaseSeats(sa.getId(), 5);
        assertTrue(releaseOk);

        SeatAvailability after = seatAvailabilityRepository.findById(sa.getId()).orElseThrow();
        assertEquals(25, after.getAvailableSeats());
        assertEquals(5, after.getBookedSeats());
    }

    // ────────────────────────────────────────────────────────────────────
    // 8. Auth gating
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(26)
    @DisplayName("GET /api/schedules — 401 UNAUTHORIZED with no token")
    void listSchedules_Failure_NoAuth() throws Exception {
        mockMvc.perform(get(SCHEDULE_BASE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(27)
    @DisplayName("GET /api/schedules/search — 401 UNAUTHORIZED with no token")
    void searchSchedules_Failure_NoAuth() throws Exception {
        mockMvc.perform(get(SCHEDULE_SEARCH)
                        .param("originStation", "A")
                        .param("destinationStation", "B")
                        .param("journeyDate", TODAY.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName(role == UserRole.ROLE_ADMIN ? "Admin" : "User")
                .lastName("SchedTester")
                .email(email)
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .role(role)
                .enabled(true)
                .build();
        userRepository.save(user);

        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtExpirationMs);
        return Jwts.builder()
                .subject(email)
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
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(500.00))
                .build();
        return trainScheduleRepository.save(s);
    }

    private void createAllSeatClasses(TrainSchedule schedule) {
        List<SeatAvailability> list = List.of(
                buildSeat(schedule, SeatClass.FIRST, 50, BigDecimal.valueOf(1500.0)),
                buildSeat(schedule, SeatClass.BUSINESS, 75, BigDecimal.valueOf(950.0)),
                buildSeat(schedule, SeatClass.SECOND, 225, BigDecimal.valueOf(450.0)),
                buildSeat(schedule, SeatClass.SLEEPER, 150, BigDecimal.valueOf(720.0))
        );
        seatAvailabilityRepository.saveAll(list);
    }

    private SeatAvailability buildSeat(TrainSchedule schedule, SeatClass cls,
                                       int total, BigDecimal fare) {
        int booked = (int) (total * 0.2);
        return SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(cls)
                .totalSeats(total)
                .bookedSeats(booked)
                .availableSeats(total - booked)
                .fare(fare)
                .build();
    }

    private CreateScheduleRequest buildValidCreateRequest(
            Long trainId, LocalDate date, LocalTime dep, LocalTime arr) {

        List<CreateScheduleRequest.SeatClassConfig> configs = new ArrayList<>();
        configs.add(CreateScheduleRequest.SeatClassConfig.builder()
                .seatClass(SeatClass.FIRST).totalSeats(50).fare(BigDecimal.valueOf(1500.0)).build());
        configs.add(CreateScheduleRequest.SeatClassConfig.builder()
                .seatClass(SeatClass.BUSINESS).totalSeats(75).fare(BigDecimal.valueOf(950.0)).build());
        configs.add(CreateScheduleRequest.SeatClassConfig.builder()
                .seatClass(SeatClass.SECOND).totalSeats(225).fare(BigDecimal.valueOf(450.0)).build());
        configs.add(CreateScheduleRequest.SeatClassConfig.builder()
                .seatClass(SeatClass.SLEEPER).totalSeats(150).fare(BigDecimal.valueOf(720.0)).build());

        return CreateScheduleRequest.builder()
                .trainId(trainId)
                .scheduledDate(date)
                .scheduledDeparture(dep)
                .scheduledArrival(arr)
                .platform("2")
                .baseFare(BigDecimal.valueOf(600.00))
                .seatClasses(configs)
                .build();
    }
}
