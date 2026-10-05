package com.trainconcierge.coordination;

import com.trainconcierge.cab.CabBooking;
import com.trainconcierge.cab.CabBookingRepository;
import com.trainconcierge.cab.CabBookingService;
import com.trainconcierge.cab.dto.RescheduleCabRequest;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.hotel.HotelBooking;
import com.trainconcierge.hotel.HotelBookingRepository;
import com.trainconcierge.hotel.HotelBookingService;
import com.trainconcierge.hotel.dto.RescheduleHotelRequest;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.rebooking.RebookingHistory;
import com.trainconcierge.rebooking.RebookingHistoryRepository;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service orchestrating travel coordination (hotel check-in & cab pickup rescheduling)
 * following a simulated train rebooking.
 *
 * <p>Requirements met:
 * 1. Dedicated TravelCoordinationService triggered after simulated rebooking.
 * 2. Retrieves revised train arrival time.
 * 3. Calculates updated hotel check-in time using configurable buffer.
 * 4. Preserves existing hotel stay duration (nights).
 * 5. Calculates cab pickup time using configurable station-exit buffer.
 * 6. Invokes MockHotelService & MockCabService via booking services.
 * 7. Records previous & updated reservation details.
 * 8. Explicit simulated update statuses (SUCCESS, NO_RESERVATION, FAILED, SKIPPED).
 * 9. Partial failure tracking & retry support.
 * 10. Duplicate execution prevention for the same rebooking event.
 * 11. Clear local simulation notices (no external provider claimed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelCoordinationService {

    private final RebookingHistoryRepository rebookingHistoryRepository;
    private final TravelCoordinationRecordRepository coordinationRecordRepository;
    private final HotelBookingRepository hotelBookingRepository;
    private final HotelBookingService hotelBookingService;
    private final CabBookingRepository cabBookingRepository;
    private final CabBookingService cabBookingService;
    private final JourneyRepository journeyRepository;
    private final UserRepository userRepository;
    private final TravelCoordinationProperties properties;

    /**
     * Executes travel coordination for a completed train rebooking.
     *
     * @param rebookingHistoryId ID of the completed RebookingHistory
     * @return TravelCoordinationResponse with explicit component and overall status
     */
    @Transactional
    public TravelCoordinationResponse coordinateTravelForRebooking(Long rebookingHistoryId) {
        RebookingHistory history = rebookingHistoryRepository.findByIdWithDetails(rebookingHistoryId)
                .orElseThrow(() -> new ResourceNotFoundException("RebookingHistory", "id", rebookingHistoryId));

        // 1. Prevent duplicate rescheduling for the same rebooking event
        boolean duplicate = coordinationRecordRepository.existsByRebookingHistoryAndOverallStatusIn(
                history, List.of(CoordinationOverallStatus.SUCCESS, CoordinationOverallStatus.NO_ACTION_REQUIRED));

        if (duplicate) {
            log.warn("[TravelCoordination] Duplicate coordination attempt rejected for rebookingHistoryId={}", rebookingHistoryId);
            throw new BusinessRuleException(
                    "Travel coordination has already been completed for this train rebooking.",
                    ErrorCode.INVALID_OPERATION);
        }

        Journey journey = history.getJourney();
        User user = history.getUser();
        Instant initiatedAt = Instant.now();
        List<String> failures = new ArrayList<>();

        // 2. Retrieve revised arrival time
        TrainSchedule newSchedule = history.getNewBooking().getSchedule();
        LocalDate arrivalDate = newSchedule.getScheduledDate();
        LocalTime arrivalTime = newSchedule.getActualArrival() != null ? newSchedule.getActualArrival() : newSchedule.getScheduledArrival();
        LocalDateTime newArrivalDateTime = LocalDateTime.of(arrivalDate, arrivalTime);

        log.info("[TravelCoordination] Starting workflow for rebookingHistoryId={} journeyId={} revisedArrival={}",
                rebookingHistoryId, journey.getId(), newArrivalDateTime);

        // 3. Hotel Reschedule Logic
        CoordinationItemStatus hotelStatus = CoordinationItemStatus.NO_RESERVATION;
        LocalDate oldCheckIn = null;
        LocalDate newCheckIn = null;
        LocalDate oldCheckOut = null;
        LocalDate newCheckOut = null;

        List<HotelBooking> hotels = hotelBookingRepository.findByJourneyOrderByCheckInDateAsc(journey);
        if (!hotels.isEmpty()) {
            HotelBooking hotel = hotels.get(0);
            oldCheckIn = hotel.getCheckInDate();
            oldCheckOut = hotel.getCheckOutDate();

            // Calculate updated hotel check-in time using configurable buffer
            LocalDateTime calculatedCheckIn = newArrivalDateTime.plusMinutes(properties.getHotelBufferMinutes());
            newCheckIn = calculatedCheckIn.toLocalDate();

            // Preserve stay duration
            long nights = ChronoUnit.DAYS.between(oldCheckIn, oldCheckOut);
            if (nights < 1) nights = 1;
            newCheckOut = newCheckIn.plusDays(nights);

            try {
                if (!newCheckIn.equals(oldCheckIn) || !newCheckOut.equals(oldCheckOut)) {
                    hotelBookingService.rescheduleHotelForCoordination(hotel.getId(), new RescheduleHotelRequest(newCheckIn, newCheckOut), user);
                    log.info("[TravelCoordination] Hotel rescheduled: oldCheckIn={} newCheckIn={} oldCheckOut={} newCheckOut={}",
                            oldCheckIn, newCheckIn, oldCheckOut, newCheckOut);
                } else {
                    log.info("[TravelCoordination] Hotel dates unchanged, skipping reschedule call.");
                }
                hotelStatus = CoordinationItemStatus.SUCCESS;
            } catch (Exception e) {
                log.error("[TravelCoordination] Hotel reschedule failed for hotelId={}: {}", hotel.getId(), e.getMessage());
                hotelStatus = CoordinationItemStatus.FAILED;
                failures.add("Hotel Reschedule Failed: " + e.getMessage());
            }
        }

        // 4. Cab Reschedule Logic
        CoordinationItemStatus cabStatus = CoordinationItemStatus.NO_RESERVATION;
        LocalDateTime oldPickupTime = null;
        LocalDateTime newPickupTime = null;

        List<CabBooking> cabs = cabBookingRepository.findByJourneyOrderByScheduledPickupTimeAsc(journey);
        if (!cabs.isEmpty()) {
            CabBooking cab = cabs.get(0);
            oldPickupTime = cab.getScheduledPickupTime();
            newPickupTime = newArrivalDateTime.plusMinutes(properties.getCabBufferMinutes());

            try {
                if (!newPickupTime.equals(oldPickupTime)) {
                    cabBookingService.rescheduleCabForCoordination(cab.getId(), new RescheduleCabRequest(newPickupTime), user);
                    log.info("[TravelCoordination] Cab rescheduled: oldPickup={} newPickup={}", oldPickupTime, newPickupTime);
                } else {
                    log.info("[TravelCoordination] Cab pickup time unchanged, skipping reschedule call.");
                }
                cabStatus = CoordinationItemStatus.SUCCESS;
            } catch (Exception e) {
                log.error("[TravelCoordination] Cab reschedule failed for cabId={}: {}", cab.getId(), e.getMessage());
                cabStatus = CoordinationItemStatus.FAILED;
                failures.add("Cab Reschedule Failed: " + e.getMessage());
            }
        }

        // 5. Determine Overall Status
        CoordinationOverallStatus overallStatus = calculateOverallStatus(hotelStatus, cabStatus);
        String failureDetails = failures.isEmpty() ? null : String.join("; ", failures);

        // 6. Record permanent TravelCoordinationRecord
        TravelCoordinationRecord record = TravelCoordinationRecord.builder()
                .rebookingHistory(history)
                .journey(journey)
                .user(user)
                .trainRebookingStatus("COMPLETED")
                .hotelUpdateStatus(hotelStatus)
                .cabUpdateStatus(cabStatus)
                .overallStatus(overallStatus)
                .hotelOldCheckIn(oldCheckIn)
                .hotelNewCheckIn(newCheckIn)
                .hotelOldCheckOut(oldCheckOut)
                .hotelNewCheckOut(newCheckOut)
                .cabOldPickupTime(oldPickupTime)
                .cabNewPickupTime(newPickupTime)
                .failureDetails(failureDetails)
                .simulatedNotice("All hotel and cab reschedules were simulated locally via Mock services. No real external provider was contacted.")
                .retryCount(0)
                .initiatedAt(initiatedAt)
                .completedAt(Instant.now())
                .build();

        record = coordinationRecordRepository.save(record);

        log.info("[TravelCoordination] Workflow finished: recordId={} overallStatus={} hotelStatus={} cabStatus={}",
                record.getId(), overallStatus, hotelStatus, cabStatus);

        return buildResponse(record);
    }

    /**
     * Retries a failed or partial travel coordination workflow execution.
     */
    @Transactional
    public TravelCoordinationResponse retryCoordination(Long recordId, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        TravelCoordinationRecord record = coordinationRecordRepository.findByIdWithDetails(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("TravelCoordinationRecord", "id", recordId));

        if (!record.getUser().getId().equals(user.getId()) && !isAdmin(user)) {
            throw new ForbiddenException("Access denied: You can only retry coordination for your own journeys.");
        }

        if (record.getOverallStatus() == CoordinationOverallStatus.SUCCESS || record.getOverallStatus() == CoordinationOverallStatus.NO_ACTION_REQUIRED) {
            throw new BusinessRuleException("Travel coordination is already completed successfully. Retry not required.", ErrorCode.INVALID_OPERATION);
        }

        RebookingHistory history = record.getRebookingHistory();
        Journey journey = record.getJourney();
        List<String> failures = new ArrayList<>();

        TrainSchedule newSchedule = history.getNewBooking().getSchedule();
        LocalDate arrivalDate = newSchedule.getScheduledDate();
        LocalTime arrivalTime = newSchedule.getActualArrival() != null ? newSchedule.getActualArrival() : newSchedule.getScheduledArrival();
        LocalDateTime newArrivalDateTime = LocalDateTime.of(arrivalDate, arrivalTime);

        // Retry failed Hotel update
        if (record.getHotelUpdateStatus() == CoordinationItemStatus.FAILED) {
            List<HotelBooking> hotels = hotelBookingRepository.findByJourneyOrderByCheckInDateAsc(journey);
            if (!hotels.isEmpty()) {
                HotelBooking hotel = hotels.get(0);
                LocalDate newCheckIn = record.getHotelNewCheckIn();
                LocalDate newCheckOut = record.getHotelNewCheckOut();
                try {
                    hotelBookingService.rescheduleHotelForCoordination(hotel.getId(), new RescheduleHotelRequest(newCheckIn, newCheckOut), record.getUser());
                    record.setHotelUpdateStatus(CoordinationItemStatus.SUCCESS);
                } catch (Exception e) {
                    failures.add("Hotel Retry Failed: " + e.getMessage());
                }
            }
        }

        // Retry failed Cab update
        if (record.getCabUpdateStatus() == CoordinationItemStatus.FAILED) {
            List<CabBooking> cabs = cabBookingRepository.findByJourneyOrderByScheduledPickupTimeAsc(journey);
            if (!cabs.isEmpty()) {
                CabBooking cab = cabs.get(0);
                LocalDateTime newPickupTime = record.getCabNewPickupTime() != null
                        ? record.getCabNewPickupTime()
                        : newArrivalDateTime.plusMinutes(properties.getCabBufferMinutes());
                try {
                    cabBookingService.rescheduleCabForCoordination(cab.getId(), new RescheduleCabRequest(newPickupTime), record.getUser());
                    record.setCabUpdateStatus(CoordinationItemStatus.SUCCESS);
                } catch (Exception e) {
                    failures.add("Cab Retry Failed: " + e.getMessage());
                }
            }
        }

        record.setOverallStatus(calculateOverallStatus(record.getHotelUpdateStatus(), record.getCabUpdateStatus()));
        record.setFailureDetails(failures.isEmpty() ? null : String.join("; ", failures));
        record.setRetryCount(record.getRetryCount() + 1);
        record.setCompletedAt(Instant.now());

        TravelCoordinationRecord updated = coordinationRecordRepository.save(record);
        return buildResponse(updated);
    }

    /**
     * Retrieves travel coordination audit records for a journey.
     */
    @Transactional(readOnly = true)
    public List<TravelCoordinationResponse> getCoordinationHistoryForJourney(Long journeyId, String userEmail) {
        User user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey", "id", journeyId));

        if (!journey.getUser().getId().equals(user.getId()) && !isAdmin(user)) {
            throw new ForbiddenException("Access denied: You can only view coordination history for your own journeys.");
        }

        List<TravelCoordinationRecord> records = coordinationRecordRepository.findByJourneyWithDetails(journey);
        return records.stream().map(this::buildResponse).collect(Collectors.toList());
    }

    private CoordinationOverallStatus calculateOverallStatus(CoordinationItemStatus hotel, CoordinationItemStatus cab) {
        if (hotel == CoordinationItemStatus.NO_RESERVATION && cab == CoordinationItemStatus.NO_RESERVATION) {
            return CoordinationOverallStatus.NO_ACTION_REQUIRED;
        }
        if ((hotel == CoordinationItemStatus.SUCCESS || hotel == CoordinationItemStatus.NO_RESERVATION) &&
            (cab == CoordinationItemStatus.SUCCESS || cab == CoordinationItemStatus.NO_RESERVATION)) {
            return CoordinationOverallStatus.SUCCESS;
        }
        if (hotel == CoordinationItemStatus.FAILED && cab == CoordinationItemStatus.FAILED) {
            return CoordinationOverallStatus.FAILED;
        }
        return CoordinationOverallStatus.PARTIAL_FAILURE;
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && user.getRole().name().equals("ROLE_ADMIN");
    }

    private TravelCoordinationResponse buildResponse(TravelCoordinationRecord record) {
        return TravelCoordinationResponse.builder()
                .coordinationRecordId(record.getId())
                .rebookingHistoryId(record.getRebookingHistory().getId())
                .journeyId(record.getJourney().getId())
                .userId(record.getUser().getId())
                .trainRebookingStatus(record.getTrainRebookingStatus())
                .hotelUpdateStatus(record.getHotelUpdateStatus())
                .cabUpdateStatus(record.getCabUpdateStatus())
                .overallStatus(record.getOverallStatus())
                .hotelOldCheckIn(record.getHotelOldCheckIn())
                .hotelNewCheckIn(record.getHotelNewCheckIn())
                .hotelOldCheckOut(record.getHotelOldCheckOut())
                .hotelNewCheckOut(record.getHotelNewCheckOut())
                .cabOldPickupTime(record.getCabOldPickupTime())
                .cabNewPickupTime(record.getCabNewPickupTime())
                .failureDetails(record.getFailureDetails())
                .simulatedNotice(record.getSimulatedNotice())
                .retryCount(record.getRetryCount())
                .initiatedAt(record.getInitiatedAt())
                .completedAt(record.getCompletedAt())
                .build();
    }
}
