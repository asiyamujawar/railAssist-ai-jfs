package com.trainconcierge.schedule;

import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.DuplicateResourceException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.dto.*;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityService;
import com.trainconcierge.seat.dto.SeatAvailabilityResponse;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainScheduleService {

    private final TrainScheduleRepository trainScheduleRepository;
    private final TrainRepository trainRepository;
    private final SeatAvailabilityService seatAvailabilityService;

    @Transactional(readOnly = true)
    public PaginatedScheduleResponse getAllSchedules(
            int page, int size, String sortBy, String sortDir) {

        Sort sort = resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TrainSchedule> schedulePage = trainScheduleRepository.findAll(pageable);
        return toPaginatedResponse(schedulePage);
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getScheduleById(Long id) {
        TrainSchedule schedule = trainScheduleRepository.findByIdWithTrain(id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainSchedule", "id", id));
        return toResponse(schedule);
    }

    @Transactional(readOnly = true)
    public List<ScheduleSearchResult> searchSchedules(
            String originStation,
            String destinationStation,
            LocalDate journeyDate) {

        if (!StringUtils.hasText(originStation) || !StringUtils.hasText(destinationStation)) {
            throw new BadRequestException(
                    "Both originStation and destinationStation are required.",
                    ErrorCode.VALIDATION_FAILED);
        }
        if (journeyDate == null) {
            throw new BadRequestException(
                    "journeyDate is required.",
                    ErrorCode.VALIDATION_FAILED);
        }

        String trimmedOrigin = originStation.trim();
        String trimmedDestination = destinationStation.trim();

        if (trimmedOrigin.equalsIgnoreCase(trimmedDestination)) {
            throw new BadRequestException(
                    "Origin and destination stations must be different.",
                    ErrorCode.INVALID_OPERATION);
        }

        List<TrainSchedule> schedules = trainScheduleRepository.findAvailableSchedulesIgnoreCase(
                trimmedOrigin, trimmedDestination, journeyDate);

        List<ScheduleSearchResult> results = new ArrayList<>();
        for (TrainSchedule schedule : schedules) {
            Train train = schedule.getTrain();

            List<SeatAvailabilityResponse> seatList = seatAvailabilityService
                    .getAvailabilityByScheduleId(schedule.getId());

            results.add(ScheduleSearchResult.builder()
                    .scheduleId(schedule.getId())
                    .trainId(train.getId())
                    .trainNumber(train.getTrainNumber())
                    .trainName(train.getTrainName())
                    .operatorName(train.getOperatorName())
                    .originStation(train.getOriginStation())
                    .destinationStation(train.getDestinationStation())
                    .scheduledDate(schedule.getScheduledDate())
                    .scheduledDeparture(schedule.getScheduledDeparture())
                    .scheduledArrival(schedule.getScheduledArrival())
                    .delayMinutes(schedule.getDelayMinutes())
                    .platform(schedule.getPlatform())
                    .cancelled(schedule.isCancelled())
                    .scheduleStatus(schedule.getScheduleStatus())
                    .baseFare(schedule.getBaseFare())
                    .seatAvailability(seatList)
                    .build());
        }

        log.debug("Schedule search {} -> {} on {} returned {} results",
                trimmedOrigin, trimmedDestination, journeyDate, results.size());
        return results;
    }

    @Transactional
    public ScheduleResponse createSchedule(CreateScheduleRequest request) {
        Train train = trainRepository.findById(request.getTrainId())
                .orElseThrow(() -> new ResourceNotFoundException("Train", "id", request.getTrainId()));

        if (!train.isActive()) {
            throw new BadRequestException(
                    "Cannot create a schedule for an inactive train.",
                    ErrorCode.INVALID_OPERATION);
        }

        LocalDate scheduledDate = request.getScheduledDate();
        LocalTime departure = request.getScheduledDeparture();
        LocalTime arrival = request.getScheduledArrival();

        if (departure.equals(arrival) || arrival.isBefore(departure)) {
            throw new BadRequestException(
                    "Scheduled arrival time must be after departure time.",
                    ErrorCode.INVALID_OPERATION);
        }

        if (trainScheduleRepository.existsByTrainAndScheduledDate(train, scheduledDate)) {
            throw new DuplicateResourceException(
                    "TrainSchedule",
                    "trainId:scheduledDate",
                    train.getId() + ":" + scheduledDate);
        }

        TrainSchedule schedule = TrainSchedule.builder()
                .train(train)
                .scheduledDate(scheduledDate)
                .scheduledDeparture(departure)
                .scheduledArrival(arrival)
                .delayMinutes(0)
                .platform(StringUtils.hasText(request.getPlatform())
                        ? request.getPlatform().trim() : null)
                .cancelled(false)
                .scheduleStatus(ScheduleStatus.SCHEDULED)
                .baseFare(request.getBaseFare())
                .build();

        TrainSchedule saved = trainScheduleRepository.save(schedule);

        if (request.getSeatClasses() != null && !request.getSeatClasses().isEmpty()) {
            seatAvailabilityService.createSeatAvailabilities(saved, request.getSeatClasses());
        }

        log.info("Created schedule: trainId={}, date={}, scheduleId={}",
                train.getId(), scheduledDate, saved.getId());
        return getScheduleById(saved.getId());
    }

    private Sort resolveSort(String sortBy, String sortDir) {
        String property = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "date", "scheduleddate" -> "scheduledDate";
            case "departure", "scheduleddeparture" -> "scheduledDeparture";
            case "arrival", "scheduledarrival" -> "scheduledArrival";
            case "delay", "delayminutes" -> "delayMinutes";
            case "created", "createdat" -> "createdAt";
            case "updated", "updatedat" -> "updatedAt";
            default -> "scheduledDate";
        };
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private ScheduleResponse toResponse(TrainSchedule s) {
        Train t = s.getTrain();
        return ScheduleResponse.builder()
                .id(s.getId())
                .trainId(t.getId())
                .trainNumber(t.getTrainNumber())
                .trainName(t.getTrainName())
                .originStation(t.getOriginStation())
                .destinationStation(t.getDestinationStation())
                .scheduledDate(s.getScheduledDate())
                .scheduledDeparture(s.getScheduledDeparture())
                .scheduledArrival(s.getScheduledArrival())
                .actualDeparture(s.getActualDeparture())
                .actualArrival(s.getActualArrival())
                .delayMinutes(s.getDelayMinutes())
                .platform(s.getPlatform())
                .cancelled(s.isCancelled())
                .scheduleStatus(s.getScheduleStatus())
                .baseFare(s.getBaseFare())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }

    private PaginatedScheduleResponse toPaginatedResponse(Page<TrainSchedule> page) {
        List<ScheduleResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PaginatedScheduleResponse.builder()
                .content(responses)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
