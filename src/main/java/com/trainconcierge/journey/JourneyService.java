package com.trainconcierge.journey;

import com.trainconcierge.booking.Booking;
import com.trainconcierge.booking.dto.BookingJourneyResponse;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.train.Train;
import com.trainconcierge.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyService {

    private final JourneyRepository journeyRepository;

    @Transactional
    public Journey createJourneyForBooking(User user, TrainSchedule schedule, Booking booking,
                                           BigDecimal bookingFare, String currency) {
        Train train = schedule.getTrain();
        Journey journey = Journey.builder()
                .user(user)
                .originStation(train.getOriginStation())
                .destinationStation(train.getDestinationStation())
                .travelDate(schedule.getScheduledDate())
                .status(JourneyStatus.PLANNED)
                .totalCost(bookingFare)
                .currency(currency)
                .build();
        Journey saved = journeyRepository.save(journey);
        booking.setJourney(saved);
        log.info("Created journey id={} for booking ref={}", saved.getId(), booking.getBookingReference());
        return saved;
    }

    @Transactional
    public void markCancelled(Journey journey) {
        if (journey == null) return;
        if (journey.getStatus() != JourneyStatus.COMPLETED) {
            journey.setStatus(JourneyStatus.CANCELLED);
            journeyRepository.save(journey);
        }
    }

    @Transactional(readOnly = true)
    public List<Journey> findByUser(User user) {
        return journeyRepository.findByUser(user);
    }

    public BookingJourneyResponse toResponse(Journey j) {
        if (j == null) return null;
        return BookingJourneyResponse.builder()
                .id(j.getId())
                .originStation(j.getOriginStation())
                .destinationStation(j.getDestinationStation())
                .travelDate(j.getTravelDate())
                .status(j.getStatus())
                .totalCost(j.getTotalCost())
                .currency(j.getCurrency())
                .notes(j.getNotes())
                .createdAt(j.getCreatedAt())
                .updatedAt(j.getUpdatedAt())
                .build();
    }
}
