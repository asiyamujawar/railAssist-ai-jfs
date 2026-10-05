package com.trainconcierge.cab;

import com.trainconcierge.auth.AuthService;
import com.trainconcierge.cab.dto.CabBookingResponse;
import com.trainconcierge.cab.dto.CabModificationResponse;
import com.trainconcierge.cab.dto.CreateCabBookingRequest;
import com.trainconcierge.cab.dto.RescheduleCabRequest;
import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.journey.Journey;
import com.trainconcierge.journey.JourneyRepository;
import com.trainconcierge.notification.NotificationService;
import com.trainconcierge.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CabBookingService {

    private final CabBookingRepository cabBookingRepository;
    private final CabBookingReferenceGenerator referenceGenerator;
    private final CabProviderAdapter cabProviderAdapter;
    private final JourneyRepository journeyRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Transactional
    public CabBookingResponse createCabBooking(CreateCabBookingRequest request) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        Journey journey = journeyRepository.findById(request.getJourneyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey", "id", request.getJourneyId()));

        if (!journey.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user attempt: caller={} tried to add cab to journey id={} owned by {}",
                    currentUser.getId(), journey.getId(), journey.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to attach cab bookings to this journey.");
        }

        LocalDateTime pickupTime = request.getScheduledPickupTime();
        validatePickupTimestamp(pickupTime);

        String cabType = (request.getCabType() != null && !request.getCabType().isBlank())
                ? request.getCabType() : "STANDARD";
        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                ? request.getCurrency() : "GBP";
        String provider = (request.getProvider() != null && !request.getProvider().isBlank())
                ? request.getProvider() : "MOCK_CAB";
        BigDecimal estimatedFare = request.getEstimatedFare() != null
                ? request.getEstimatedFare() : BigDecimal.ZERO;

        CabBooking booking = CabBooking.builder()
                .user(currentUser)
                .journey(journey)
                .pickupAddress(request.getPickupAddress())
                .dropoffAddress(request.getDropoffAddress())
                .scheduledPickupTime(pickupTime)
                .cabType(cabType)
                .estimatedFare(estimatedFare)
                .currency(currency)
                .provider(provider)
                .cabBookingReference(referenceGenerator.generate())
                .status(CabBookingStatus.CONFIRMED_SIMULATED)
                .confirmedAt(Instant.now())
                .modifications(new ArrayList<>())
                .build();

        SimulatedCabResult confirmResult = cabProviderAdapter.confirmReservation(booking);
        booking.setExternalBookingRef(confirmResult.externalBookingRef());

        CabBooking saved = cabBookingRepository.save(booking);

        log.info("Cab booking created: ref={} user={} journey={} pickupTime={} pickup={} dropoff={} status={}",
                saved.getCabBookingReference(), currentUser.getId(), journey.getId(),
                pickupTime, saved.getPickupAddress(), saved.getDropoffAddress(), saved.getStatus());

        // Phase 17: in-app notification
        try {
            notificationService.notifyCabConfirmed(
                    currentUser, saved.getId(),
                    saved.getPickupAddress(), saved.getDropoffAddress(),
                    pickupTime.toString());
        } catch (Exception ex) {
            log.warn("[Notification] Could not create cab-confirmed notification: {}", ex.getMessage());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CabBookingResponse> getCabsForJourney(Long journeyId) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey", "id", journeyId));

        if (!journey.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user attempt: caller={} tried to list cabs on journey id={} owned by {}",
                    currentUser.getId(), journeyId, journey.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to view cab bookings for this journey.");
        }

        List<CabBooking> results = cabBookingRepository.findByJourneyOrderByScheduledPickupTimeAsc(journey);

        return results.stream().map(this::toResponse).toList();
    }

    @Transactional
    public CabBookingResponse rescheduleCab(Long cabId, RescheduleCabRequest request) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        CabBooking booking = cabBookingRepository.findByIdWithDetails(cabId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CabBooking", "id", cabId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user reschedule attempt caller={} cabBookingId={} owner={}",
                    currentUser.getId(), cabId, booking.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to reschedule this cab booking.");
        }

        return executeReschedule(booking, request);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CabBookingResponse rescheduleCabForCoordination(Long cabId, RescheduleCabRequest request, User expectedOwner) {
        CabBooking booking = cabBookingRepository.findByIdWithDetails(cabId)
                .orElseThrow(() -> new ResourceNotFoundException("CabBooking", "id", cabId));

        if (!booking.getUser().getId().equals(expectedOwner.getId())) {
            log.warn("Cross-user reschedule attempt in coordination cabBookingId={} expectedOwner={} actualOwner={}",
                    cabId, expectedOwner.getId(), booking.getUser().getId());
            throw new ForbiddenException("You are not permitted to reschedule this cab booking.");
        }

        return executeReschedule(booking, request);
    }

    private CabBookingResponse executeReschedule(CabBooking booking, RescheduleCabRequest request) {
        LocalDateTime oldPickupTime = booking.getScheduledPickupTime();
        LocalDateTime newPickupTime = request.getNewScheduledPickupTime();

        if (newPickupTime == null) {
            throw new BadRequestException(
                    "newScheduledPickupTime is required.",
                    ErrorCode.CAB_INVALID_PICKUP_TIME);
        }

        if (newPickupTime.equals(oldPickupTime)) {
            throw new BusinessRuleException(
                    "New pickup time is identical to the existing pickup time.",
                    ErrorCode.CAB_RESCHEDULE_NO_CHANGES);
        }

        validatePickupTimestamp(newPickupTime);

        SimulatedCabResult rescheduleResult = cabProviderAdapter.reschedule(
                booking, oldPickupTime, newPickupTime);

        CabModificationAudit audit = CabModificationAudit.builder()
                .cabBooking(booking)
                .oldPickupTime(oldPickupTime)
                .newPickupTime(newPickupTime)
                .rescheduledAt(Instant.now())
                .simulationProvider("MOCK")
                .build();
        booking.getModifications().add(audit);

        booking.setScheduledPickupTime(newPickupTime);
        booking.setStatus(CabBookingStatus.RESCHEDULED_SIMULATED);
        booking.setRescheduledAt(Instant.now());
        if (rescheduleResult.externalBookingRef() != null) {
            booking.setExternalBookingRef(rescheduleResult.externalBookingRef());
        }

        CabBooking saved = cabBookingRepository.save(booking);

        log.info("Cab booking rescheduled: ref={} user={} newPickupTime={} status={}",
                saved.getCabBookingReference(), booking.getUser().getId(),
                newPickupTime, saved.getStatus());

        // Phase 17: in-app notification
        try {
            notificationService.notifyCabRescheduled(
                    booking.getUser(), saved.getId(),
                    saved.getPickupAddress(), newPickupTime.toString());
        } catch (Exception ex) {
            log.warn("[Notification] Could not create cab-rescheduled notification: {}", ex.getMessage());
        }

        return toResponse(saved);
    }

    private void validatePickupTimestamp(LocalDateTime pickupTime) {
        if (pickupTime == null) {
            throw new BadRequestException(
                    "scheduledPickupTime is required.",
                    ErrorCode.CAB_INVALID_PICKUP_TIME);
        }
        if (pickupTime.isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(
                    "scheduledPickupTime cannot be in the past.",
                    ErrorCode.CAB_INVALID_PICKUP_TIME);
        }
    }

    private CabBookingResponse toResponse(CabBooking b) {
        Long journeyId = b.getJourney() != null ? b.getJourney().getId() : null;

        List<CabModificationResponse> modifications = new ArrayList<>();
        if (b.getModifications() != null) {
            modifications = b.getModifications().stream()
                    .map(m -> CabModificationResponse.builder()
                            .oldPickupTime(m.getOldPickupTime())
                            .newPickupTime(m.getNewPickupTime())
                            .rescheduledAt(m.getRescheduledAt())
                            .simulationProvider(m.getSimulationProvider())
                            .build())
                    .sorted(Comparator.comparing(CabModificationResponse::getRescheduledAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        return CabBookingResponse.builder()
                .id(b.getId())
                .userId(b.getUser().getId())
                .journeyId(journeyId)
                .pickupAddress(b.getPickupAddress())
                .dropoffAddress(b.getDropoffAddress())
                .scheduledPickupTime(b.getScheduledPickupTime())
                .actualPickupTime(b.getActualPickupTime())
                .cabType(b.getCabType())
                .estimatedFare(b.getEstimatedFare())
                .actualFare(b.getActualFare())
                .currency(b.getCurrency())
                .provider(b.getProvider())
                .status(b.getStatus())
                .cabBookingReference(b.getCabBookingReference())
                .externalBookingRef(b.getExternalBookingRef())
                .confirmedAt(b.getConfirmedAt())
                .rescheduledAt(b.getRescheduledAt())
                .modifications(modifications)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
