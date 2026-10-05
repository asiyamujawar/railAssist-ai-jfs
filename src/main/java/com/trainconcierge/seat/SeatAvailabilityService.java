package com.trainconcierge.seat;

import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.BusinessRuleException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.dto.SeatAvailabilityResponse;
import com.trainconcierge.seat.dto.SeatAvailabilityUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatAvailabilityService {

    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final TrainScheduleRepository trainScheduleRepository;

    @Transactional(readOnly = true)
    public List<SeatAvailabilityResponse> getAvailabilityByScheduleId(Long scheduleId) {
        TrainSchedule schedule = trainScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainSchedule", "id", scheduleId));

        List<SeatAvailability> list = seatAvailabilityRepository.findAllByScheduleIdWithDetails(scheduleId);
        return list.stream().map(this::toResponse).toList();
    }

    @Transactional
    public SeatAvailabilityResponse updateAvailability(
            Long scheduleId, SeatAvailabilityUpdateRequest request) {

        TrainSchedule schedule = trainScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("TrainSchedule", "id", scheduleId));

        SeatAvailability sa = seatAvailabilityRepository
                .findByScheduleAndSeatClass(schedule, request.getSeatClass())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SeatAvailability", "scheduleId:seatClass",
                        scheduleId + ":" + request.getSeatClass()));

        Integer total = request.getTotalSeats();
        Integer available = request.getAvailableSeats();
        Integer booked = request.getBookedSeats();
        BigDecimal fare = request.getFare();

        if (total == null) total = sa.getTotalSeats();
        if (available == null) available = sa.getAvailableSeats();
        if (booked == null) booked = sa.getBookedSeats();
        if (fare == null) fare = sa.getFare();

        if (total < 0 || available < 0 || booked < 0) {
            throw new BadRequestException(
                    "Seat counts (total, available, booked) cannot be negative.",
                    ErrorCode.VALIDATION_FAILED);
        }

        if (available + booked > total) {
            throw new BadRequestException(
                    "Available + booked seats must not exceed total seats.",
                    ErrorCode.VALIDATION_FAILED);
        }

        int updated = seatAvailabilityRepository.updateInventoryAndFareAtomically(
                sa.getId(), total, available, booked, fare);

        if (updated == 0) {
            throw new BusinessRuleException(
                    "Seat availability update rejected — negative count guard triggered.",
                    ErrorCode.INVALID_OPERATION);
        }

        SeatAvailability refreshed = seatAvailabilityRepository.findById(sa.getId())
                .orElseThrow(() -> new ResourceNotFoundException("SeatAvailability", "id", sa.getId()));

        log.info("Admin updated seat availability: scheduleId={}, class={}, total={}, available={}, booked={}, fare={}",
                scheduleId, request.getSeatClass(), total, available, booked, fare);

        return toResponse(refreshed);
    }

    @Transactional
    public boolean bookSeats(Long seatAvailabilityId, int seats) {
        if (seats <= 0) {
            throw new BadRequestException(
                    "Number of seats to book must be positive.",
                    ErrorCode.VALIDATION_FAILED);
        }
        int updated = seatAvailabilityRepository.bookSeatsAtomically(seatAvailabilityId, seats);
        return updated == 1;
    }

    @Transactional
    public boolean releaseSeats(Long seatAvailabilityId, int seats) {
        if (seats <= 0) {
            throw new BadRequestException(
                    "Number of seats to release must be positive.",
                    ErrorCode.VALIDATION_FAILED);
        }
        int updated = seatAvailabilityRepository.releaseSeatsAtomically(seatAvailabilityId, seats);
        return updated == 1;
    }

    @Transactional
    public List<SeatAvailability> createSeatAvailabilities(
            TrainSchedule schedule,
            List<com.trainconcierge.schedule.dto.CreateScheduleRequest.SeatClassConfig> configs) {

        List<SeatAvailability> result = new ArrayList<>();
        for (var config : configs) {
            if (config.getTotalSeats() < 0) {
                throw new BadRequestException(
                        "Total seats for class " + config.getSeatClass() + " must not be negative.",
                        ErrorCode.VALIDATION_FAILED);
            }
            SeatAvailability sa = SeatAvailability.builder()
                    .schedule(schedule)
                    .seatClass(config.getSeatClass())
                    .totalSeats(config.getTotalSeats())
                    .availableSeats(config.getTotalSeats())
                    .bookedSeats(0)
                    .fare(config.getFare())
                    .build();
            result.add(sa);
        }
        return seatAvailabilityRepository.saveAll(result);
    }

    private SeatAvailabilityResponse toResponse(SeatAvailability sa) {
        return SeatAvailabilityResponse.builder()
                .id(sa.getId())
                .scheduleId(sa.getSchedule().getId())
                .seatClass(sa.getSeatClass())
                .totalSeats(sa.getTotalSeats())
                .availableSeats(sa.getAvailableSeats())
                .bookedSeats(sa.getBookedSeats())
                .fare(sa.getFare())
                .updatedAt(sa.getUpdatedAt())
                .build();
    }
}
