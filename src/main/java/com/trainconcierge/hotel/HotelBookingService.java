package com.trainconcierge.hotel;

import com.trainconcierge.auth.AuthService;
import com.trainconcierge.exception.*;
import com.trainconcierge.hotel.dto.CreateHotelBookingRequest;
import com.trainconcierge.hotel.dto.HotelBookingResponse;
import com.trainconcierge.hotel.dto.HotelModificationResponse;
import com.trainconcierge.hotel.dto.RescheduleHotelRequest;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotelBookingService {

    private final HotelBookingRepository hotelBookingRepository;
    private final HotelBookingReferenceGenerator referenceGenerator;
    private final HotelProviderAdapter hotelProviderAdapter;
    private final JourneyRepository journeyRepository;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Transactional
    public HotelBookingResponse createHotelBooking(CreateHotelBookingRequest request) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        Journey journey = journeyRepository.findById(request.getJourneyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey", "id", request.getJourneyId()));

        if (!journey.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user attempt: caller={} tried to add hotel to journey id={} owned by {}",
                    currentUser.getId(), journey.getId(), journey.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to attach hotel bookings to this journey.");
        }

        LocalDate checkIn = request.getCheckInDate();
        LocalDate checkOut = request.getCheckOutDate();
        validateDatePair(checkIn, checkOut);

        int computedNights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        Integer suppliedNights = request.getNumberOfNights();
        int finalNights;
        if (suppliedNights != null) {
            if (suppliedNights != computedNights) {
                throw new BadRequestException(
                        "numberOfNights does not match checkIn/checkOut dates.",
                        ErrorCode.HOTEL_INVALID_DATES);
            }
            finalNights = suppliedNights;
        } else {
            finalNights = computedNights;
        }

        BigDecimal totalCost = request.getTotalCost() != null
                ? request.getTotalCost() : BigDecimal.ZERO;
        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                ? request.getCurrency() : "GBP";

        HotelBooking booking = HotelBooking.builder()
                .user(currentUser)
                .journey(journey)
                .hotelName(request.getHotelName())
                .city(request.getCity())
                .hotelAddress(request.getHotelAddress())
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numberOfNights(finalNights)
                .totalCost(totalCost)
                .currency(currency)
                .hotelBookingReference(referenceGenerator.generate())
                .status(HotelBookingStatus.CONFIRMED_SIMULATED)
                .confirmedAt(Instant.now())
                .modifications(new ArrayList<>())
                .build();

        SimulatedHotelResult confirmResult = hotelProviderAdapter.confirmReservation(booking);
        booking.setExternalBookingRef(confirmResult.externalBookingRef());

        HotelBooking saved = hotelBookingRepository.save(booking);

        log.info("Hotel booking created: ref={} user={} journey={} nights={} hotel={} status={}",
                saved.getHotelBookingReference(), currentUser.getId(), journey.getId(),
                finalNights, saved.getHotelName(), saved.getStatus());

        // Phase 17: in-app notification
        try {
            notificationService.notifyHotelConfirmed(
                    currentUser, saved.getId(),
                    saved.getHotelName(),
                    checkIn.toString(), checkOut.toString(), finalNights);
        } catch (Exception ex) {
            log.warn("[Notification] Could not create hotel-confirmed notification: {}", ex.getMessage());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<HotelBookingResponse> getHotelsForJourney(Long journeyId) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Journey", "id", journeyId));

        if (!journey.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user attempt: caller={} tried to list hotels on journey id={} owned by {}",
                    currentUser.getId(), journeyId, journey.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to view hotel bookings for this journey.");
        }

        List<HotelBooking> results = hotelBookingRepository.findByJourneyOrderByCheckInDateAsc(journey);

        return results.stream().map(this::toResponse).toList();
    }

    @Transactional
    public HotelBookingResponse rescheduleHotel(Long hotelId, RescheduleHotelRequest request) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        HotelBooking booking = hotelBookingRepository.findByIdWithDetails(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "HotelBooking", "id", hotelId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user reschedule attempt caller={} hotelBookingId={} owner={}",
                    currentUser.getId(), hotelId, booking.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to reschedule this hotel booking.");
        }

        return executeReschedule(booking, request);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public HotelBookingResponse rescheduleHotelForCoordination(Long hotelId, RescheduleHotelRequest request, User expectedOwner) {
        HotelBooking booking = hotelBookingRepository.findByIdWithDetails(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("HotelBooking", "id", hotelId));

        if (!booking.getUser().getId().equals(expectedOwner.getId())) {
            log.warn("Cross-user reschedule attempt in coordination hotelBookingId={} expectedOwner={} actualOwner={}",
                    hotelId, expectedOwner.getId(), booking.getUser().getId());
            throw new ForbiddenException("You are not permitted to reschedule this hotel booking.");
        }

        return executeReschedule(booking, request);
    }

    private HotelBookingResponse executeReschedule(HotelBooking booking, RescheduleHotelRequest request) {
        LocalDate oldCheckIn = booking.getCheckInDate();
        LocalDate oldCheckOut = booking.getCheckOutDate();

        LocalDate newCheckIn = request.getNewCheckInDate() != null
                ? request.getNewCheckInDate() : oldCheckIn;
        LocalDate newCheckOut = request.getNewCheckOutDate() != null
                ? request.getNewCheckOutDate() : oldCheckOut;

        if (request.getNewCheckInDate() == null && request.getNewCheckOutDate() == null) {
            throw new BadRequestException(
                    "At least one of newCheckInDate or newCheckOutDate must be provided.",
                    ErrorCode.HOTEL_RESCHEDULE_NO_CHANGES);
        }

        if (newCheckIn.equals(oldCheckIn) && newCheckOut.equals(oldCheckOut)) {
            throw new BusinessRuleException(
                    "New dates are identical to the existing dates.",
                    ErrorCode.HOTEL_RESCHEDULE_NO_CHANGES);
        }

        validateDatePair(newCheckIn, newCheckOut);

        SimulatedHotelResult rescheduleResult = hotelProviderAdapter.reschedule(
                booking, oldCheckIn, oldCheckOut, newCheckIn, newCheckOut);

        HotelModificationAudit audit = HotelModificationAudit.builder()
                .hotelBooking(booking)
                .oldCheckInDate(oldCheckIn)
                .newCheckInDate(newCheckIn)
                .oldCheckOutDate(oldCheckOut)
                .newCheckOutDate(newCheckOut)
                .rescheduledAt(Instant.now())
                .simulationProvider("MOCK")
                .build();
        booking.getModifications().add(audit);

        booking.setCheckInDate(newCheckIn);
        booking.setCheckOutDate(newCheckOut);
        booking.setNumberOfNights((int) ChronoUnit.DAYS.between(newCheckIn, newCheckOut));
        booking.setStatus(HotelBookingStatus.RESCHEDULED_SIMULATED);
        booking.setRescheduledAt(Instant.now());
        if (rescheduleResult.externalBookingRef() != null) {
            booking.setExternalBookingRef(rescheduleResult.externalBookingRef());
        }

        HotelBooking saved = hotelBookingRepository.save(booking);

        log.info("Hotel booking rescheduled: ref={} user={} newCheckIn={} newCheckOut={} status={}",
                saved.getHotelBookingReference(), booking.getUser().getId(),
                newCheckIn, newCheckOut, saved.getStatus());

        // Phase 17: in-app notification
        try {
            notificationService.notifyHotelRescheduled(
                    booking.getUser(), saved.getId(),
                    saved.getHotelName(),
                    newCheckIn.toString(), newCheckOut.toString());
        } catch (Exception ex) {
            log.warn("[Notification] Could not create hotel-rescheduled notification: {}", ex.getMessage());
        }

        return toResponse(saved);
    }

    private void validateDatePair(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException(
                    "Both checkInDate and checkOutDate are required.",
                    ErrorCode.HOTEL_INVALID_DATES);
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BusinessRuleException(
                    "checkOutDate must be strictly after checkInDate.",
                    ErrorCode.HOTEL_INVALID_DATES);
        }
    }

    private HotelBookingResponse toResponse(HotelBooking b) {
        Long journeyId = b.getJourney() != null ? b.getJourney().getId() : null;

        List<HotelModificationResponse> modifications = new ArrayList<>();
        if (b.getModifications() != null) {
            modifications = b.getModifications().stream()
                    .map(m -> HotelModificationResponse.builder()
                            .oldCheckInDate(m.getOldCheckInDate())
                            .newCheckInDate(m.getNewCheckInDate())
                            .oldCheckOutDate(m.getOldCheckOutDate())
                            .newCheckOutDate(m.getNewCheckOutDate())
                            .rescheduledAt(m.getRescheduledAt())
                            .simulationProvider(m.getSimulationProvider())
                            .build())
                    .sorted(Comparator.comparing(HotelModificationResponse::getRescheduledAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
        }

        return HotelBookingResponse.builder()
                .id(b.getId())
                .userId(b.getUser().getId())
                .journeyId(journeyId)
                .hotelName(b.getHotelName())
                .city(b.getCity())
                .hotelAddress(b.getHotelAddress())
                .checkInDate(b.getCheckInDate())
                .checkOutDate(b.getCheckOutDate())
                .numberOfNights(b.getNumberOfNights())
                .totalCost(b.getTotalCost())
                .currency(b.getCurrency())
                .status(b.getStatus())
                .hotelBookingReference(b.getHotelBookingReference())
                .externalBookingRef(b.getExternalBookingRef())
                .confirmedAt(b.getConfirmedAt())
                .rescheduledAt(b.getRescheduledAt())
                .modifications(modifications)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
