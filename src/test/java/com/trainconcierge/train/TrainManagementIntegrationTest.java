package com.trainconcierge.train;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.admin.AdminTrainController;
import com.trainconcierge.auth.AuthService;
import com.trainconcierge.auth.dto.RegisterRequest;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.dto.CreateTrainRequest;
import com.trainconcierge.train.dto.UpdateTrainRequest;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrainManagementIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private static final String PUBLIC_LIST_URL = "/api/trains";
    private static final String PUBLIC_SEARCH_URL = "/api/trains/search";
    private static final String ADMIN_URL = "/api/admin/trains";

    private static final String ADMIN_EMAIL = "admin.train@example.com";
    private static final String USER_EMAIL = "user.train@example.com";
    private static final String TEST_PASSWORD = "Train@2026";

    private String adminToken;
    private String userToken;

    private Train trainA;
    private Train trainB;
    private Train trainC;
    private TrainSchedule scheduleA_today;
    private TrainSchedule scheduleB_today;

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

        trainA = createAndSaveTrain("TR001", "Express One", "New Delhi", "Agra", 400);
        trainB = createAndSaveTrain("TR002", "Express Two", "Agra", "New Delhi", 400);
        trainC = createAndSaveTrain("TR003", "Superfast A", "Mumbai Central", "New Delhi", 600);

        LocalDate today = LocalDate.now();
        scheduleA_today = createAndSaveSchedule(trainA, today,
                LocalTime.of(8, 0), LocalTime.of(10, 30));
        scheduleB_today = createAndSaveSchedule(trainB, today,
                LocalTime.of(12, 0), LocalTime.of(14, 30));
        createAndSaveSchedule(trainA, today.plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(10, 30));

        createSeatAvailabilities(scheduleA_today);
        createSeatAvailabilities(scheduleB_today);
    }

    // ────────────────────────────────────────────────────────────────────
    // 1. Public — GET /api/trains (paginated list)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("GET /api/trains — 200 OK paginated list (authenticated USER)")
    void listTrains_Success_AsUser() throws Exception {
        mockMvc.perform(get(PUBLIC_LIST_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("page", "0")
                        .param("size", "2")
                        .param("sortBy", "trainNumber")
                        .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.pageNumber").value(0))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false))
                .andExpect(jsonPath("$.data.content[0].trainNumber").value("TR001"))
                .andExpect(jsonPath("$.data.content[1].trainNumber").value("TR002"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/trains — 200 OK page 1 sorting by totalSeats desc")
    void listTrains_Success_Page1_WithSorting() throws Exception {
        mockMvc.perform(get(PUBLIC_LIST_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "2")
                        .param("sortBy", "totalSeats")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.first").value(false))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/trains/{id} — 200 OK with valid id")
    void getTrainById_Success() throws Exception {
        mockMvc.perform(get(PUBLIC_LIST_URL + "/" + trainA.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(trainA.getId()))
                .andExpect(jsonPath("$.data.trainNumber").value("TR001"))
                .andExpect(jsonPath("$.data.trainName").value("Express One"))
                .andExpect(jsonPath("$.data.originStation").value("New Delhi"))
                .andExpect(jsonPath("$.data.destinationStation").value("Agra"))
                .andExpect(jsonPath("$.data.totalSeats").value(400))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty());
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/trains/{id} — 404 NOT FOUND with non-existent id")
    void getTrainById_Failure_NotFound() throws Exception {
        mockMvc.perform(get(PUBLIC_LIST_URL + "/99999")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()))
                .andExpect(jsonPath("$.message", containsString("Train")))
                .andExpect(jsonPath("$.message", containsString("99999")));
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. Public — GET /api/trains/search
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("GET /api/trains/search — 200 OK case-insensitive route match")
    void searchTrains_Success_CaseInsensitive() throws Exception {
        String today = LocalDate.now().toString();

        // Case-insensitive: "new delhi" instead of "New Delhi"
        mockMvc.perform(get(PUBLIC_SEARCH_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "new delhi")
                        .param("destinationStation", "AGRA")
                        .param("journeyDate", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].trainNumber").value("TR001"))
                .andExpect(jsonPath("$.data[0].originStation").value("New Delhi"))
                .andExpect(jsonPath("$.data[0].journeyDate").value(today))
                .andExpect(jsonPath("$.data[0].scheduledDeparture").isNotEmpty())
                .andExpect(jsonPath("$.data[0].scheduledArrival").isNotEmpty())
                .andExpect(jsonPath("$.data[0].cancelled").value(false))
                .andExpect(jsonPath("$.data[0].availableClasses").isArray())
                .andExpect(jsonPath("$.data[0].availableClasses", hasSize(4)))
                .andExpect(jsonPath("$.data[0].availableClasses[0].seatClass",
                        anyOf(is("FIRST"), is("BUSINESS"), is("SECOND"), is("SLEEPER"))))
                .andExpect(jsonPath("$.data[0].availableClasses[0].availableSeats").isNumber());
    }

    @Test
    @Order(6)
    @DisplayName("GET /api/trains/search — 200 OK empty list when route has no service")
    void searchTrains_Success_EmptyForNonServicedRoute() throws Exception {
        String today = LocalDate.now().toString();

        mockMvc.perform(get(PUBLIC_SEARCH_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "Kolkata")
                        .param("destinationStation", "Chennai")
                        .param("journeyDate", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("no trains found")));
    }

    @Test
    @Order(7)
    @DisplayName("GET /api/trains/search — 400 BAD REQUEST when origin == destination")
    void searchTrains_Failure_InvalidRoute_SameStation() throws Exception {
        String today = LocalDate.now().toString();

        mockMvc.perform(get(PUBLIC_SEARCH_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "New Delhi")
                        .param("destinationStation", "new delhi")
                        .param("journeyDate", today))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("origin and destination stations must be different")));
    }

    @Test
    @Order(8)
    @DisplayName("GET /api/trains/search — 400 BAD REQUEST when journeyDate is in the past")
    void searchTrains_Failure_PastDate() throws Exception {
        String yesterday = LocalDate.now().minusDays(1).toString();

        mockMvc.perform(get(PUBLIC_SEARCH_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "New Delhi")
                        .param("destinationStation", "Agra")
                        .param("journeyDate", yesterday))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("past")));
    }

    @Test
    @Order(9)
    @DisplayName("GET /api/trains/search — 400 BAD REQUEST missing parameters")
    void searchTrains_Failure_MissingParams() throws Exception {
        String today = LocalDate.now().toString();

        mockMvc.perform(get(PUBLIC_SEARCH_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .param("originStation", "New Delhi")
                        .param("journeyDate", today))
                .andExpect(status().isBadRequest());
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. Admin — POST /api/admin/trains (create)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("POST /api/admin/trains — 201 CREATED as ADMIN")
    void createTrain_Success_AsAdmin() throws Exception {
        CreateTrainRequest req = CreateTrainRequest.builder()
                .trainNumber("TR999")
                .trainName("Super Express 999")
                .operatorName("TestRail")
                .originStation("Chennai")
                .destinationStation("Bangalore")
                .totalSeats(500)
                .build();

        MvcResult result = mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("created")))
                .andExpect(jsonPath("$.data.trainNumber").value("TR999"))
                .andExpect(jsonPath("$.data.trainName").value("Super Express 999"))
                .andExpect(jsonPath("$.data.operatorName").value("TestRail"))
                .andExpect(jsonPath("$.data.originStation").value("Chennai"))
                .andExpect(jsonPath("$.data.destinationStation").value("Bangalore"))
                .andExpect(jsonPath("$.data.totalSeats").value(500))
                .andExpect(jsonPath("$.data.active").value(true))
                .andReturn();

        // Idempotency check: duplicate number must fail
        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_ALREADY_EXISTS.name()))
                .andExpect(jsonPath("$.message", containsString("TR999")));
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/admin/trains — 403 FORBIDDEN as non-admin ROLE_USER")
    void createTrain_Failure_AsRoleUser() throws Exception {
        CreateTrainRequest req = CreateTrainRequest.builder()
                .trainNumber("TR888")
                .trainName("Denied Express")
                .originStation("A")
                .destinationStation("B")
                .totalSeats(100)
                .build();

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    @Order(12)
    @DisplayName("POST /api/admin/trains — 400 BAD REQUEST origin equals destination")
    void createTrain_Failure_SameStation() throws Exception {
        CreateTrainRequest req = CreateTrainRequest.builder()
                .trainNumber("TR777")
                .trainName("Bad Route")
                .originStation("Same")
                .destinationStation("Same")
                .totalSeats(100)
                .build();

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()))
                .andExpect(jsonPath("$.message",
                        containsStringIgnoringCase("origin and destination stations must be different")));
    }

    @Test
    @Order(13)
    @DisplayName("POST /api/admin/trains — 400 BAD REQUEST validation failures")
    void createTrain_Failure_ValidationErrors() throws Exception {
        CreateTrainRequest req = CreateTrainRequest.builder()
                .trainNumber("")
                .trainName("")
                .originStation("")
                .destinationStation("")
                .totalSeats(0)
                .build();

        mockMvc.perform(post(ADMIN_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.validationErrors.trainNumber").isNotEmpty())
                .andExpect(jsonPath("$.validationErrors.totalSeats").isNotEmpty());
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. Admin — PUT /api/admin/trains/{id} (update)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(14)
    @DisplayName("PUT /api/admin/trains/{id} — 200 OK partial update as ADMIN")
    void updateTrain_Success_PartialFields() throws Exception {
        UpdateTrainRequest req = UpdateTrainRequest.builder()
                .trainName("Express One — Renamed")
                .totalSeats(450)
                .build();

        mockMvc.perform(put(ADMIN_URL + "/" + trainA.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.trainName").value("Express One — Renamed"))
                .andExpect(jsonPath("$.data.totalSeats").value(450))
                .andExpect(jsonPath("$.data.originStation").value("New Delhi"))
                .andExpect(jsonPath("$.data.destinationStation").value("Agra"));
    }

    @Test
    @Order(15)
    @DisplayName("PUT /api/admin/trains/{id} — 404 NOT FOUND missing train")
    void updateTrain_Failure_NotFound() throws Exception {
        UpdateTrainRequest req = UpdateTrainRequest.builder()
                .trainName("Ghost")
                .build();

        mockMvc.perform(put(ADMIN_URL + "/77777")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @Order(16)
    @DisplayName("PUT /api/admin/trains/{id} — 400 update to same station origin/dest")
    void updateTrain_Failure_BadStationChange() throws Exception {
        // Try to set destination equal to origin (they differ currently)
        UpdateTrainRequest req = UpdateTrainRequest.builder()
                .destinationStation("New Delhi")
                .build();

        mockMvc.perform(put(ADMIN_URL + "/" + trainA.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_OPERATION.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 5. Admin — PATCH /api/admin/trains/{id}/deactivate (soft delete)
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(17)
    @DisplayName("PATCH /api/admin/trains/{id}/deactivate — 200 OK as ADMIN")
    void deactivateTrain_Success() throws Exception {
        // First confirm it's active and visible in the public list
        mockMvc.perform(get(PUBLIC_LIST_URL + "/" + trainA.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));

        // Deactivate
        mockMvc.perform(patch(ADMIN_URL + "/" + trainA.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("deactivated")));

        // Fetch by id still works (shows the entity with active=false, not deleted)
        mockMvc.perform(get(PUBLIC_LIST_URL + "/" + trainA.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));
    }

    @Test
    @Order(18)
    @DisplayName("PATCH /api/admin/trains/{id}/deactivate — 403 FORBIDDEN as USER")
    void deactivateTrain_Failure_AsRoleUser() throws Exception {
        mockMvc.perform(patch(ADMIN_URL + "/" + trainB.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    @Order(19)
    @DisplayName("PATCH /api/admin/trains/{id}/deactivate — 404 NOT FOUND")
    void deactivateTrain_Failure_NotFound() throws Exception {
        mockMvc.perform(patch(ADMIN_URL + "/11111/deactivate")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    // ────────────────────────────────────────────────────────────────────
    // 6. Endpoint visibility & auth gating
    // ────────────────────────────────────────────────────────────────────

    @Test
    @Order(20)
    @DisplayName("Public trains list — 401 UNAUTHORIZED with no token")
    void listTrains_Failure_NoAuth() throws Exception {
        mockMvc.perform(get(PUBLIC_LIST_URL))
                .andExpect(status().isUnauthorized());
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    private String createUserAndGetToken(String email, UserRole role) {
        User user = User.builder()
                .firstName(role == UserRole.ROLE_ADMIN ? "Admin" : "User")
                .lastName("Tester")
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
                                     String origin, String destination, int seats) {
        Train t = Train.builder()
                .trainNumber(number)
                .trainName(name)
                .operatorName("TestOps")
                .originStation(origin)
                .destinationStation(destination)
                .totalSeats(seats)
                .active(true)
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
                .build();
        return trainScheduleRepository.save(s);
    }

    private void createSeatAvailabilities(TrainSchedule schedule) {
        List<SeatAvailability> list = List.of(
                buildSeat(schedule, SeatClass.FIRST, 40),
                buildSeat(schedule, SeatClass.BUSINESS, 60),
                buildSeat(schedule, SeatClass.SECOND, 180),
                buildSeat(schedule, SeatClass.SLEEPER, 120)
        );
        seatAvailabilityRepository.saveAll(list);
    }

    private SeatAvailability buildSeat(TrainSchedule schedule, SeatClass cls, int total) {
        int booked = (int) (total * 0.25);
        return SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(cls)
                .totalSeats(total)
                .bookedSeats(booked)
                .availableSeats(total - booked)
                .build();
    }
}
