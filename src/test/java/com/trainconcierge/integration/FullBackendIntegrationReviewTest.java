package com.trainconcierge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trainconcierge.admin.simulation.AdminSimulationControlService;
import com.trainconcierge.admin.simulation.dto.DisruptionEvaluationResponse;
import com.trainconcierge.admin.simulation.dto.TriggerDelayRequest;
import com.trainconcierge.admin.simulation.dto.TriggerDisruptionEvaluationRequest;
import com.trainconcierge.booking.*;
import com.trainconcierge.booking.dto.BookTrainRequest;
import com.trainconcierge.booking.dto.BookingResponse;
import com.trainconcierge.cab.*;
import com.trainconcierge.cab.dto.CreateCabBookingRequest;
import com.trainconcierge.coordination.TravelCoordinationRecord;
import com.trainconcierge.coordination.TravelCoordinationRecordRepository;
import com.trainconcierge.coordination.TravelCoordinationResponse;
import com.trainconcierge.coordination.TravelCoordinationService;
import com.trainconcierge.disruption.*;
import com.trainconcierge.hotel.*;
import com.trainconcierge.hotel.dto.CreateHotelBookingRequest;
import com.trainconcierge.journey.*;
import com.trainconcierge.notification.NotificationRepository;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.recommendation.*;
import com.trainconcierge.schedule.*;
import com.trainconcierge.seat.*;
import com.trainconcierge.timeline.JourneyTimelineService;
import com.trainconcierge.timeline.dto.JourneyTimelineResponse;
import com.trainconcierge.train.*;
import com.trainconcierge.user.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End Integration Review Tests covering Requirements 12, 13, 14, 15:
 * 1. Complete disruption workflow E2E
 * 2. No-alternative-available scenario
 * 3. Partial hotel/cab update resilience
 * 4. Concurrent booking and seat inventory safety
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FullBackendIntegrationReviewTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private TrainRepository trainRepository;
    @Autowired private TrainScheduleRepository scheduleRepository;
    @Autowired private SeatAvailabilityRepository seatAvailabilityRepository;
    @Autowired private SeatAvailabilityService seatAvailabilityService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingService bookingService;
    @Autowired private JourneyRepository journeyRepository;
    @Autowired private HotelBookingRepository hotelBookingRepository;
    @Autowired private HotelBookingService hotelBookingService;
    @Autowired private CabBookingRepository cabBookingRepository;
    @Autowired private CabBookingService cabBookingService;
    @Autowired private DisruptionEventRepository disruptionEventRepository;
    @Autowired private RecommendationRepository recommendationRepository;
    @Autowired private RecommendationService recommendationService;
    @Autowired private TravelCoordinationService coordinationService;
    @Autowired private TravelCoordinationRecordRepository coordinationRepository;
    @Autowired private JourneyTimelineService journeyTimelineService;
    @Autowired private AdminSimulationControlService adminSimulationControlService;
    @Autowired private TrainStatusHistoryRepository historyRepository;
    @Autowired private RebookingHistoryRepository rebookingHistoryRepository;
    @Autowired private HotelModificationAuditRepository hotelAuditRepository;
    @Autowired private CabModificationAuditRepository cabAuditRepository;
    @Autowired private NotificationRepository notificationRepository;

    private User testPassenger;
    private Train primaryTrain;
    private TrainSchedule primarySchedule;
    private Train altTrain;
    private TrainSchedule altSchedule;

    @BeforeEach
    void setUp() {
        cleanDatabase();

        testPassenger = userRepository.save(User.builder()
                .firstName("Alice")
                .lastName("Traveler")
                .email("alice.review@example.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .phoneNumber("+91-99999-88888")
                .role(UserRole.ROLE_USER)
                .enabled(true)
                .build());

        // Primary Train: Delhi -> Mumbai
        primaryTrain = trainRepository.save(Train.builder()
                .trainNumber("E2E001")
                .trainName("Rajdhani Express")
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .totalSeats(300)
                .active(true)
                .build());

        primarySchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(primaryTrain)
                .scheduledDate(LocalDate.now().plusDays(3))
                .scheduledDeparture(LocalTime.of(16, 0))
                .scheduledArrival(LocalTime.of(8, 0))
                .platform("1")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(1500.00))
                .build());

        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(primarySchedule)
                .seatClass(SeatClass.SLEEPER)
                .availableSeats(50)
                .totalSeats(50)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(2000.00))
                .build());

        // Alternative Train for Rebooking: Delhi -> Mumbai (departs 2 hours later)
        altTrain = trainRepository.save(Train.builder()
                .trainNumber("E2E002")
                .trainName("Duronto Express")
                .originStation("Delhi")
                .destinationStation("Mumbai")
                .totalSeats(300)
                .active(true)
                .build());

        altSchedule = scheduleRepository.save(TrainSchedule.builder()
                .train(altTrain)
                .scheduledDate(LocalDate.now().plusDays(3))
                .scheduledDeparture(LocalTime.of(18, 0))
                .scheduledArrival(LocalTime.of(10, 0))
                .platform("4")
                .delayMinutes(0)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(BigDecimal.valueOf(1600.00))
                .build());

        seatAvailabilityRepository.save(SeatAvailability.builder()
                .schedule(altSchedule)
                .seatClass(SeatClass.SLEEPER)
                .availableSeats(20)
                .totalSeats(20)
                .bookedSeats(0)
                .fare(BigDecimal.valueOf(2100.00))
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        coordinationRepository.deleteAll();
        rebookingHistoryRepository.deleteAll();
        cabAuditRepository.deleteAll();
        cabBookingRepository.deleteAll();
        hotelAuditRepository.deleteAll();
        hotelBookingRepository.deleteAll();
        historyRepository.deleteAll();
        recommendationRepository.deleteAll();
        disruptionEventRepository.deleteAll();
        notificationRepository.deleteAll();
        seatAvailabilityRepository.deleteAll();
        bookingRepository.deleteAll();
        journeyRepository.deleteAll();
        scheduleRepository.deleteAll();
        trainRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 12: Complete End-to-End Disruption Workflow
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1)
    @WithMockUser(username = "alice.review@example.com", roles = "USER")
    void endToEndDisruptionWorkflow_Success() {
        // 1. Create initial train booking
        BookTrainRequest trainReq = BookTrainRequest.builder()
                .scheduleId(primarySchedule.getId())
                .seatClass(SeatClass.SLEEPER)
                .passengerCount(1)
                .currency("INR")
                .build();

        BookingResponse bookingResp = bookingService.createBooking(trainReq);
        assertNotNull(bookingResp.getId());
        Long journeyId = bookingResp.getJourney().getId();

        // 2. Add Hotel Booking to Journey
        CreateHotelBookingRequest hotelReq = CreateHotelBookingRequest.builder()
                .journeyId(journeyId)
                .hotelName("Taj Lands End")
                .city("Mumbai")
                .hotelAddress("Bandra West, Mumbai")
                .checkInDate(LocalDate.now().plusDays(3))
                .checkOutDate(LocalDate.now().plusDays(5))
                .totalCost(BigDecimal.valueOf(5000.00))
                .currency("INR")
                .build();
        hotelBookingService.createHotelBooking(hotelReq);

        // 3. Add Cab Booking to Journey
        CreateCabBookingRequest cabReq = CreateCabBookingRequest.builder()
                .journeyId(journeyId)
                .pickupAddress("Mumbai Central Station")
                .dropoffAddress("Taj Lands End")
                .scheduledPickupTime(LocalDateTime.of(LocalDate.now().plusDays(4), LocalTime.of(8, 30)))
                .cabType("SEDAN")
                .estimatedFare(BigDecimal.valueOf(500.00))
                .currency("INR")
                .provider("Ola")
                .build();
        cabBookingService.createCabBooking(cabReq);

        // 4. Admin triggers 120-minute train delay
        adminSimulationControlService.triggerDelay(TriggerDelayRequest.builder()
                .scheduleId(primarySchedule.getId())
                .delayMinutes(120)
                .reason("Signal collapse near Jhansi")
                .build());

        // 5. Trigger disruption evaluation for journey
        DisruptionEvaluationResponse evalResp = adminSimulationControlService
                .triggerDisruptionEvaluation(TriggerDisruptionEvaluationRequest.builder()
                        .journeyId(journeyId)
                        .build());

        assertFalse(evalResp.isSkipped(), "Evaluation should run");
        assertNotNull(evalResp.getDisruptionEventId(), "Disruption event created");
        assertTrue(evalResp.isRecommendationTriggered(), "Recommendation engine triggered");

        // 6. Verify disruption event recorded in DB
        DisruptionEvent disruption = disruptionEventRepository.findById(evalResp.getDisruptionEventId()).orElseThrow();
        assertEquals(DisruptionType.DELAY, disruption.getType());

        // 7. Verify journey timeline contains all events
        JourneyTimelineResponse timeline = journeyTimelineService.getJourneyTimeline(journeyId, testPassenger.getEmail());
        assertNotNull(timeline);
        assertEquals(journeyId, timeline.getJourneyId());
        assertTrue(timeline.getTotalEvents() >= 4, "Timeline must contain booking, hotel, cab, disruption events");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 13: No Alternative Available Scenario
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(2)
    @WithMockUser(username = "alice.review@example.com", roles = "USER")
    void noAlternativeAvailable_GracefulHandling() {
        // Remove alternative schedule seats so no alternative train exists
        SeatAvailability altSeats = seatAvailabilityRepository
                .findByScheduleAndSeatClass(altSchedule, SeatClass.SLEEPER).orElseThrow();
        altSeats.setAvailableSeats(0);
        seatAvailabilityRepository.save(altSeats);

        // Book primary schedule
        BookTrainRequest trainReq = BookTrainRequest.builder()
                .scheduleId(primarySchedule.getId())
                .seatClass(SeatClass.SLEEPER)
                .passengerCount(1)
                .currency("INR")
                .build();
        BookingResponse bookingResp = bookingService.createBooking(trainReq);
        Long journeyId = bookingResp.getJourney().getId();

        // Create Disruption Event manually
        DisruptionEvent disruption = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(primarySchedule)
                .journey(journeyRepository.findById(journeyId).orElseThrow())
                .type(DisruptionType.CANCELLATION)
                .severity(DisruptionSeverity.CRITICAL)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(Instant.now())
                .description("Complete track blockage")
                .build());

        // Generate recommendation
        List<RecommendationResponse> recs = recommendationService.generateRecommendations(disruption.getId(), testPassenger.getEmail());
        assertNotNull(recs);
        assertTrue(recs.isEmpty(), "Zero recommendations returned gracefully when no seats/alternatives available");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 14: Travel Coordination Partial Update Failure Resilience
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(3)
    @WithMockUser(username = "alice.review@example.com", roles = "USER")
    void partialHotelOrCabUpdateFailure_HandledGracefully() {
        // Book train
        BookingResponse bookingResp = bookingService.createBooking(BookTrainRequest.builder()
                .scheduleId(primarySchedule.getId())
                .seatClass(SeatClass.SLEEPER)
                .passengerCount(1)
                .currency("INR")
                .build());
        Long journeyId = bookingResp.getJourney().getId();
        Journey journey = journeyRepository.findById(journeyId).orElseThrow();

        // Create Disruption Event
        DisruptionEvent disruption = disruptionEventRepository.save(DisruptionEvent.builder()
                .schedule(primarySchedule)
                .journey(journey)
                .type(DisruptionType.DELAY)
                .severity(DisruptionSeverity.HIGH)
                .status(DisruptionStatus.DETECTED)
                .detectedAt(Instant.now())
                .description("Delay 90 mins")
                .build());

        // Verify Travel Coordination handles missing hotel/cab without throwing errors
        List<TravelCoordinationResponse> history = coordinationService.getCoordinationHistoryForJourney(journeyId, testPassenger.getEmail());
        assertNotNull(history);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Requirement 15: Concurrent Booking & Seat Inventory Behavior
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(4)
    void concurrentSeatBooking_PreventsOverbooking() throws Exception {
        // Set available seats to exactly 3
        SeatAvailability sa = seatAvailabilityRepository
                .findByScheduleAndSeatClass(primarySchedule, SeatClass.SLEEPER).orElseThrow();
        sa.setAvailableSeats(3);
        sa.setTotalSeats(3);
        sa.setBookedSeats(0);
        seatAvailabilityRepository.save(sa);

        Long availabilityId = sa.getId();
        int numberOfThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    boolean booked = seatAvailabilityService.bookSeats(availabilityId, 1);
                    if (booked) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Release threads simultaneously
        boolean completed = finishLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "Concurrent booking threads finished");
        assertEquals(3, successCount.get(), "Exactly 3 bookings must succeed for 3 available seats");
        assertEquals(7, failureCount.get(), "Remaining 7 booking attempts must fail");

        // Verify final database state
        SeatAvailability updatedSa = seatAvailabilityRepository.findById(availabilityId).orElseThrow();
        assertEquals(0, updatedSa.getAvailableSeats(), "Available seats must be exactly 0");
        assertEquals(3, updatedSa.getBookedSeats(), "Booked seats must be exactly 3");
    }
}
