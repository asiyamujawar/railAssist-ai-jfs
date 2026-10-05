package com.trainconcierge.booking;

import com.trainconcierge.auth.AuthService;
import com.trainconcierge.booking.dto.BookTrainRequest;
import com.trainconcierge.booking.dto.BookingJourneyResponse;
import com.trainconcierge.booking.dto.BookingResponse;
import com.trainconcierge.booking.dto.PaginatedBookingResponse;
import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.DuplicateResourceException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.journey.JourneyService;
import com.trainconcierge.notification.NotificationService;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatAvailabilityService;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.Train;
import com.trainconcierge.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TrainScheduleRepository scheduleRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final SeatAvailabilityService seatAvailabilityService;
    private final JourneyService journeyService;
    private final BookingReferenceGenerator referenceGenerator;
    private final AuthService authService;
    private final NotificationService notificationService;

    @Transactional
    public BookingResponse createBooking(BookTrainRequest request) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        int passengerCount = request.getPassengerCount();
        if (passengerCount <= 0) {
            throw new BadRequestException(
                    "Passenger count must be positive.",
                    ErrorCode.PASSENGER_COUNT_INVALID);
        }
        if (passengerCount > 9) {
            throw new BadRequestException(
                    "Passenger count must not exceed 9 per booking.",
                    ErrorCode.PASSENGER_COUNT_INVALID);
        }

        TrainSchedule schedule = scheduleRepository.findByIdWithTrain(request.getScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TrainSchedule", "id", request.getScheduleId()));

        if (schedule.isCancelled()) {
            throw new BusinessRuleException(
                    "Schedule has been cancelled. Booking not permitted.",
                    ErrorCode.SCHEDULE_CANCELLED);
        }

        Train train = schedule.getTrain();
        if (!train.isActive()) {
            throw new BusinessRuleException(
                    "Train master record is inactive. Booking not permitted.",
                    ErrorCode.INVALID_OPERATION);
        }

        SeatClass seatClass = request.getSeatClass();
        SeatAvailability availability = seatAvailabilityRepository
                .findByScheduleAndSeatClass(schedule, seatClass)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SeatAvailability", "scheduleId:seatClass",
                        schedule.getId() + ":" + seatClass));

        if (availability.getAvailableSeats() < passengerCount) {
            throw new BusinessRuleException(
                    "Insufficient seats. Available: " + availability.getAvailableSeats()
                            + ", requested: " + passengerCount,
                    ErrorCode.INSUFFICIENT_SEATS);
        }

        if (bookingRepository.existsActiveDuplicate(currentUser, schedule, seatClass)) {
            throw new DuplicateResourceException(
                    "Active booking already exists for this user on schedule "
                            + schedule.getId() + " class " + seatClass,
                    "user+schedule+seatClass",
                    currentUser.getId() + "+" + schedule.getId() + "+" + seatClass);
        }

        boolean booked = seatAvailabilityService.bookSeats(availability.getId(), passengerCount);
        if (!booked) {
            throw new BusinessRuleException(
                    "Seats could not be reserved — concurrent booking race. Please retry.",
                    ErrorCode.INSUFFICIENT_SEATS);
        }

        BigDecimal perSeatFare = availability.getFare() != null
                ? availability.getFare() : BigDecimal.ZERO;
        BigDecimal totalFare = perSeatFare.multiply(BigDecimal.valueOf(passengerCount));
        BigDecimal baseFare = schedule.getBaseFare() != null
                ? schedule.getBaseFare() : BigDecimal.ZERO;

        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                ? request.getCurrency() : "INR";

        Booking booking = Booking.builder()
                .bookingReference(referenceGenerator.generate())
                .user(currentUser)
                .schedule(schedule)
                .seatClass(seatClass)
                .numberOfSeats(passengerCount)
                .baseFare(baseFare)
                .totalFare(totalFare)
                .currency(currency)
                .status(BookingStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .build();

        Booking savedBooking = bookingRepository.save(booking);

        journeyService.createJourneyForBooking(currentUser, schedule, savedBooking,
                totalFare, currency);
        savedBooking = bookingRepository.save(savedBooking);

        log.info("Booking created: ref={}, user={}, schedule={}, class={}, seats={}, fare={}",
                savedBooking.getBookingReference(), currentUser.getId(),
                schedule.getId(), seatClass, passengerCount, totalFare);

        // Phase 17: in-app notification
        try {
            notificationService.notifyBookingConfirmed(
                    currentUser,
                    savedBooking.getId(),
                    savedBooking.getBookingReference(),
                    train.getTrainName(),
                    schedule.getScheduledDate() != null ? schedule.getScheduledDate().toString() : "N/A");
        } catch (Exception ex) {
            log.warn("[Notification] Could not create booking-confirmed notification: {}", ex.getMessage());
        }

        return toResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public PaginatedBookingResponse getMyBookings(int page, int size,
                                                  String sortBy, String sortDir) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = resolveSort(sortBy, direction);

        Pageable pageable = PageRequest.of(safePage, safeSize, sort);
        Page<Booking> paged = bookingRepository.findPageByUserWithDetails(currentUser, pageable);

        List<BookingResponse> content = paged.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PaginatedBookingResponse.builder()
                .content(content)
                .pageNumber(paged.getNumber())
                .pageSize(paged.getSize())
                .totalElements(paged.getTotalElements())
                .totalPages(paged.getTotalPages())
                .first(paged.isFirst())
                .last(paged.isLast())
                .empty(paged.isEmpty())
                .build();
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Long bookingId) {
        User currentUser = authService.getCurrentAuthenticatedUser();
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user access attempt on booking id={}: caller={}, owner={}",
                    bookingId, currentUser.getId(), booking.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to access this booking.");
        }

        return toResponse(booking);
    }

    @Transactional
    public BookingResponse cancelBooking(Long bookingId) {
        User currentUser = authService.getCurrentAuthenticatedUser();

        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));

        if (!booking.getUser().getId().equals(currentUser.getId())) {
            log.warn("Cross-user cancel attempt on booking id={}: caller={}, owner={}",
                    bookingId, currentUser.getId(), booking.getUser().getId());
            throw new ForbiddenException(
                    "You are not permitted to cancel this booking.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED
                || booking.getStatus() == BookingStatus.REFUNDED) {
            throw new BusinessRuleException(
                    "Booking is already cancelled.",
                    ErrorCode.BOOKING_ALREADY_CANCELLED);
        }

        if (booking.getStatus() == BookingStatus.COMPLETED) {
            throw new BusinessRuleException(
                    "Cannot cancel a completed journey booking.",
                    ErrorCode.BOOKING_NOT_CANCELLABLE);
        }

        int seatsToRelease = booking.getNumberOfSeats();
        SeatAvailability sa = seatAvailabilityRepository
                .findByScheduleAndSeatClass(booking.getSchedule(), booking.getSeatClass())
                .orElse(null);
        if (sa != null) {
            boolean released = seatAvailabilityService.releaseSeats(sa.getId(), seatsToRelease);
            if (!released) {
                log.error("Seat release failed for booking ref={} seats={}",
                        booking.getBookingReference(), seatsToRelease);
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(Instant.now());

        journeyService.markCancelled(booking.getJourney());

        Booking saved = bookingRepository.save(booking);
        log.info("Booking cancelled: ref={}, seatsReleased={}",
                saved.getBookingReference(), seatsToRelease);

        // Phase 17: in-app notification
        try {
            String trainName = saved.getSchedule() != null && saved.getSchedule().getTrain() != null
                    ? saved.getSchedule().getTrain().getTrainName() : "train";
            notificationService.notifyBookingCancelled(
                    currentUser, saved.getId(), saved.getBookingReference(), trainName);
        } catch (Exception ex) {
            log.warn("[Notification] Could not create booking-cancelled notification: {}", ex.getMessage());
        }

        return toResponse(saved);
    }

    private Sort resolveSort(String sortBy, Sort.Direction dir) {
        if (sortBy == null) sortBy = "createdAt";
        return switch (sortBy) {
            case "status" -> Sort.by(dir, "status");
            case "journeyDate" -> Sort.by(dir, "schedule.scheduledDate");
            case "totalFare" -> Sort.by(dir, "totalFare");
            case "updatedAt" -> Sort.by(dir, "updatedAt");
            default -> Sort.by(dir, "createdAt");
        };
    }

    private BookingResponse toResponse(Booking b) {
        TrainSchedule s = b.getSchedule();
        Train t = s.getTrain();
        User u = b.getUser();
        BookingJourneyResponse journey = b.getJourney() != null
                ? journeyService.toResponse(b.getJourney()) : null;

        String passengerName = u.getFirstName() + " " + u.getLastName();

        return BookingResponse.builder()
                .id(b.getId())
                .bookingReference(b.getBookingReference())
                .userId(u.getId())
                .passengerName(passengerName)
                .scheduleId(s.getId())
                .trainId(t.getId())
                .trainNumber(t.getTrainNumber())
                .trainName(t.getTrainName())
                .originStation(t.getOriginStation())
                .destinationStation(t.getDestinationStation())
                .journeyDate(s.getScheduledDate())
                .scheduledDeparture(s.getScheduledDeparture())
                .scheduledArrival(s.getScheduledArrival())
                .platform(s.getPlatform())
                .seatClass(b.getSeatClass())
                .numberOfSeats(b.getNumberOfSeats())
                .baseFare(b.getBaseFare())
                .totalFare(b.getTotalFare())
                .currency(b.getCurrency())
                .status(b.getStatus())
                .confirmedAt(b.getConfirmedAt())
                .cancelledAt(b.getCancelledAt())
                .seatNumbers(b.getSeatNumbers())
                .journey(journey)
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}
