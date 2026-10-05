package com.trainconcierge.rebooking;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.BookingReferenceGenerator;
import com.trainconcierge.booking.BookingRepository;
import com.trainconcierge.booking.BookingStatus;
import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.disruption.DisruptionStatus;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.journey.JourneyStatus;
import com.trainconcierge.notification.NotificationService;
import com.trainconcierge.rebooking.dto.RebookRequest;
import com.trainconcierge.rebooking.dto.RebookingHistoryResponse;
import com.trainconcierge.rebooking.dto.RebookingResponse;
import com.trainconcierge.recommendation.Recommendation;
import com.trainconcierge.recommendation.RecommendationRepository;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service handling simulated train rebooking operations.
 *
 * <p>Requirements met:
 * 1. Validate disruption belongs to authenticated user's journey.
 * 2. Verify selected recommendation is still eligible.
 * 3. Recheck available seats before booking.
 * 4. Database transaction for all booking and inventory changes.
 * 5. Mark original booking as REBOOKED (do NOT delete).
 * 6. Create new simulated booking linked to original booking and journey.
 * 7. Update seat inventory atomically via SeatAvailabilityRepository.
 * 8. Create RebookingHistory audit record.
 * 9. Update disruption status (RESOLVED).
 * 10. Prevent duplicate rebooking of the same disruption.
 * 11. Support user-approved mode and controlled demo auto-rebooking mode.
 * 12. Handle insufficient seats and concurrent rebooking attempts safely.
 * 13. Trigger post-rebooking workflow after transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RebookingService {

    private final DisruptionEventRepository disruptionEventRepository;
    private final RecommendationRepository recommendationRepository;
    private final BookingRepository bookingRepository;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final RebookingHistoryRepository rebookingHistoryRepository;
    private final BookingReferenceGenerator bookingReferenceGenerator;
    private final PostRebookingWorkflowTrigger postRebookingWorkflowTrigger;
    private final NotificationService notificationService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Executes a rebooking for a disruption event using a selected recommendation.
     *
     * @param disruptionId ID of the disruption event
     * @param request recommendation selection and mode
     * @param userEmail authenticated user's email
     * @return RebookingResponse containing original and new booking details
     */
    @Transactional
    public RebookingResponse rebook(Long disruptionId, RebookRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        DisruptionEvent disruption = disruptionEventRepository.findById(disruptionId)
                .orElseThrow(() -> new ResourceNotFoundException("DisruptionEvent", "id", disruptionId));

        // 1. Prevent duplicate rebooking
        if (disruption.isResolved() || rebookingHistoryRepository.existsByDisruptionEventAndStatus(disruption, RebookingStatus.COMPLETED)) {
            throw new BusinessRuleException(
                    "This disruption event has already been resolved or rebooked.",
                    ErrorCode.DISRUPTION_ALREADY_REBOOKED);
        }

        // 2. Ownership & Access Validation
        Journey journey = disruption.getJourney();
        final Booking originalBooking = resolveOriginalBooking(disruption, journey, user);
        if (journey == null) {
            journey = originalBooking.getJourney();
        }

        // 3. Verify selected recommendation
        Recommendation recommendation = recommendationRepository.findById(request.getRecommendationId())
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation", "id", request.getRecommendationId()));

        if (!recommendation.getDisruptionEvent().getId().equals(disruptionId)) {
            throw new BusinessRuleException(
                    "Selected recommendation does not belong to disruption id: " + disruptionId,
                    ErrorCode.RECOMMENDATION_NOT_ELIGIBLE);
        }

        TrainSchedule newSchedule = recommendation.getSuggestedSchedule();
        if (newSchedule.isCancelled() || newSchedule.getScheduleStatus() == ScheduleStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "The selected alternative train schedule has been cancelled.",
                    ErrorCode.RECOMMENDATION_NOT_ELIGIBLE);
        }

        // 4. Seat availability check and reservation
        int seatsNeeded = originalBooking.getNumberOfSeats();
        SeatAvailability seatAvail = seatAvailabilityRepository
                .findByScheduleAndSeatClass(newSchedule, originalBooking.getSeatClass())
                .orElseThrow(() -> new BusinessRuleException(
                        "No seat availability found for seat class: " + originalBooking.getSeatClass(),
                        ErrorCode.SEAT_NOT_AVAILABLE));

        if (seatAvail.getAvailableSeats() < seatsNeeded) {
            throw new BusinessRuleException(
                    "Insufficient seats available on alternative train. Required: " + seatsNeeded + ", available: " + seatAvail.getAvailableSeats(),
                    ErrorCode.INSUFFICIENT_SEATS);
        }

        // 5. Atomic Seat Inventory update
        int updated = seatAvailabilityRepository.bookSeatsAtomically(seatAvail.getId(), seatsNeeded);
        if (updated == 0) {
            throw new BusinessRuleException(
                    "Failed to reserve seats due to concurrent booking. Please try again.",
                    ErrorCode.INSUFFICIENT_SEATS);
        }

        // 6. Create new simulated booking
        BigDecimal unitFare = seatAvail.getFare() != null ? seatAvail.getFare() : newSchedule.getBaseFare();
        BigDecimal newTotalFare = unitFare.multiply(BigDecimal.valueOf(seatsNeeded));

        Booking newBooking = Booking.builder()
                .bookingReference(bookingReferenceGenerator.generate())
                .user(user)
                .schedule(newSchedule)
                .journey(journey)
                .status(BookingStatus.CONFIRMED)
                .seatClass(originalBooking.getSeatClass())
                .numberOfSeats(seatsNeeded)
                .baseFare(unitFare)
                .totalFare(newTotalFare)
                .currency(originalBooking.getCurrency())
                .seatNumbers("AUTO-" + (seatAvail.getBookedSeats() + 1))
                .confirmedAt(Instant.now())
                .build();

        newBooking = bookingRepository.save(newBooking);

        // 7. Mark original booking as REBOOKED & link replacement
        originalBooking.setStatus(BookingStatus.REBOOKED);
        originalBooking.setReplacementBooking(newBooking);
        bookingRepository.save(originalBooking);

        // 8. Update Disruption & Recommendation status
        disruption.setStatus(DisruptionStatus.RESOLVED);
        disruption.setResolved(true);
        disruption.setResolvedAt(Instant.now());
        disruptionEventRepository.save(disruption);

        recommendation.setStatus("ACCEPTED");
        recommendationRepository.save(recommendation);

        if (journey != null) {
            journey.setStatus(JourneyStatus.REBOOKED);
            journeyRepository.save(journey);
        }

        // 9. Create RebookingHistory record
        BigDecimal fareDiff = newTotalFare.subtract(originalBooking.getTotalFare());
        RebookingHistory history = RebookingHistory.builder()
                .user(user)
                .disruptionEvent(disruption)
                .journey(journey)
                .originalBooking(originalBooking)
                .newBooking(newBooking)
                .recommendation(recommendation)
                .status(RebookingStatus.COMPLETED)
                .autonomous(request.isAutoMode())
                .fareDifference(fareDiff)
                .initiatedAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        history = rebookingHistoryRepository.save(history);

        log.info("[RebookingService] Rebooking COMPLETED for disruptionId={} user={} origRef={} newRef={} fareDiff={}",
                disruptionId, userEmail, originalBooking.getBookingReference(), newBooking.getBookingReference(), fareDiff);

        entityManager.flush();
        entityManager.clear();

        RebookingHistory fullHistory = rebookingHistoryRepository.findByIdWithDetails(history.getId()).orElse(history);
        RebookingResponse response = buildRebookingResponse(fullHistory);

        // Phase 17: REBOOKING_CONFIRMED in-app notification
        try {
            String newArrival = newSchedule.getScheduledArrival() != null
                    ? newSchedule.getScheduledArrival().toString() : "N/A";
            String newTrainName = newSchedule.getTrain() != null
                    ? newSchedule.getTrain().getTrainName() : "replacement train";
            notificationService.notifyRebookingConfirmed(
                    user, history.getId(),
                    newBooking.getBookingReference(),
                    newTrainName,
                    newSchedule.getScheduledDate() != null ? newSchedule.getScheduledDate().toString() : "N/A",
                    newArrival);
        } catch (Exception ex) {
            log.warn("[Notification] Could not create rebooking-confirmed notification: {}", ex.getMessage());
        }

        // 10. Trigger post-rebooking event workflow
        postRebookingWorkflowTrigger.triggerPostRebookingWorkflow(fullHistory);

        return response;
    }

    /**
     * Gets all rebooking history entries for a specific journey.
     *
     * @param journeyId ID of the journey
     * @param userEmail authenticated user's email
     * @return list of RebookingHistoryResponse
     */
    @Transactional(readOnly = true)
    public List<RebookingHistoryResponse> getRebookingHistory(Long journeyId, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey", "id", journeyId));

        if (!journey.getUser().getId().equals(user.getId()) && !isAdmin(user)) {
            throw new ForbiddenException("Access denied: You can only view rebooking history for your own journeys.");
        }

        List<RebookingHistory> histories = rebookingHistoryRepository.findByJourneyWithDetails(journey);

        return histories.stream()
                .map(this::buildHistoryResponse)
                .collect(Collectors.toList());
    }

    private Booking resolveOriginalBooking(DisruptionEvent disruption, Journey journey, User user) {
        Booking orig = null;
        if (journey != null) {
            if (!journey.getUser().getId().equals(user.getId()) && !isAdmin(user)) {
                throw new ForbiddenException("Access denied: Disruption event does not belong to your journey.");
            }
            List<Booking> journeyBookings = bookingRepository.findByJourney(journey);
            orig = journeyBookings.stream()
                    .filter(b -> b.getSchedule().getId().equals(disruption.getSchedule().getId()))
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.PENDING)
                    .findFirst()
                    .orElse(null);
        }

        if (orig == null) {
            List<Booking> userBookings = bookingRepository.findByScheduleAndStatus(disruption.getSchedule(), BookingStatus.CONFIRMED);
            orig = userBookings.stream()
                    .filter(b -> b.getUser().getId().equals(user.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(
                            "No active confirmed booking found for the disrupted train schedule.",
                            ErrorCode.BOOKING_NOT_OWNER));
        }
        return orig;
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole().name().equals("ROLE_ADMIN");
    }

    private RebookingResponse buildRebookingResponse(RebookingHistory history) {
        Booking orig = history.getOriginalBooking();
        Booking newB = history.getNewBooking();
        TrainSchedule origSched = orig.getSchedule();
        TrainSchedule newSched = newB.getSchedule();

        return RebookingResponse.builder()
                .rebookingHistoryId(history.getId())
                .disruptionEventId(history.getDisruptionEvent().getId())
                .journeyId(history.getJourney() != null ? history.getJourney().getId() : null)
                .originalBookingId(orig.getId())
                .originalBookingReference(orig.getBookingReference())
                .originalTrainNumber(origSched.getTrain() != null ? origSched.getTrain().getTrainNumber() : null)
                .originalTrainName(origSched.getTrain() != null ? origSched.getTrain().getTrainName() : null)
                .originalJourneyDate(origSched.getScheduledDate())
                .originalDeparture(origSched.getScheduledDeparture())
                .originalArrival(origSched.getScheduledArrival())
                .originalFare(orig.getTotalFare())
                .originalBookingStatus(orig.getStatus().name())
                .newBookingId(newB.getId())
                .newBookingReference(newB.getBookingReference())
                .newTrainNumber(newSched.getTrain() != null ? newSched.getTrain().getTrainNumber() : null)
                .newTrainName(newSched.getTrain() != null ? newSched.getTrain().getTrainName() : null)
                .newJourneyDate(newSched.getScheduledDate())
                .newDeparture(newSched.getScheduledDeparture())
                .newArrival(newSched.getScheduledArrival())
                .newFare(newB.getTotalFare())
                .newBookingStatus(newB.getStatus().name())
                .numberOfSeats(newB.getNumberOfSeats())
                .seatClass(newB.getSeatClass().name())
                .currency(newB.getCurrency())
                .fareDifference(history.getFareDifference())
                .rebookingStatus(history.getStatus())
                .autonomous(history.isAutonomous())
                .initiatedAt(history.getInitiatedAt())
                .completedAt(history.getCompletedAt())
                .build();
    }

    private RebookingHistoryResponse buildHistoryResponse(RebookingHistory history) {
        return RebookingHistoryResponse.builder()
                .id(history.getId())
                .disruptionEventId(history.getDisruptionEvent().getId())
                .journeyId(history.getJourney() != null ? history.getJourney().getId() : null)
                .originalBookingId(history.getOriginalBooking().getId())
                .originalBookingReference(history.getOriginalBooking().getBookingReference())
                .newBookingId(history.getNewBooking() != null ? history.getNewBooking().getId() : null)
                .newBookingReference(history.getNewBooking() != null ? history.getNewBooking().getBookingReference() : null)
                .recommendationId(history.getRecommendation() != null ? history.getRecommendation().getId() : null)
                .status(history.getStatus())
                .autonomous(history.isAutonomous())
                .fareDifference(history.getFareDifference())
                .failureReason(history.getFailureReason())
                .initiatedAt(history.getInitiatedAt())
                .completedAt(history.getCompletedAt())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
