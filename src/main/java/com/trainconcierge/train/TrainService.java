package com.trainconcierge.train;

import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.DuplicateResourceException;
import com.trainconcierge.exception.ErrorCode;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.dto.*;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;

    // ─────────────────────────────────────────────────────────────────────
    // Public read operations
    // ─────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PaginatedTrainResponse getAllTrains(
            int page, int size, String sortBy, String sortDir, boolean includeInactive) {

        Sort sort = resolveSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Train> trainPage = includeInactive
                ? trainRepository.findAll(pageable)
                : trainRepository.findAllByActiveTrue(pageable);

        return toPaginatedResponse(trainPage);
    }

    @Transactional(readOnly = true)
    public TrainResponse getTrainById(Long id) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train", "id", id));
        return toResponse(train);
    }

    @Transactional(readOnly = true)
    public TrainResponse getTrainByTrainNumber(String trainNumber) {
        Train train = trainRepository.findByTrainNumber(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train", "trainNumber", trainNumber));
        return toResponse(train);
    }

    @Transactional(readOnly = true)
    public List<TrainSearchResult> searchTrains(
            String originStation,
            String destinationStation,
            LocalDate journeyDate) {

        if (!StringUtils.hasText(originStation) || !StringUtils.hasText(destinationStation)) {
            throw new BadRequestException(
                    "Both originStation and destinationStation are required for search.",
                    ErrorCode.VALIDATION_FAILED);
        }

        if (journeyDate == null) {
            throw new BadRequestException(
                    "journeyDate is required for search.",
                    ErrorCode.VALIDATION_FAILED);
        }

        String trimmedOrigin = originStation.trim();
        String trimmedDestination = destinationStation.trim();

        if (trimmedOrigin.equalsIgnoreCase(trimmedDestination)) {
            throw new BadRequestException(
                    "Origin and destination stations must be different.",
                    ErrorCode.INVALID_OPERATION);
        }

        if (journeyDate.isBefore(LocalDate.now())) {
            throw new BadRequestException(
                    "Journey date cannot be in the past.",
                    ErrorCode.INVALID_OPERATION);
        }

        List<TrainSchedule> schedules = trainScheduleRepository.findAvailableSchedulesIgnoreCase(
                trimmedOrigin, trimmedDestination, journeyDate);

        List<TrainSearchResult> results = new ArrayList<>();
        for (TrainSchedule schedule : schedules) {
            Train train = schedule.getTrain();

            List<TrainSearchResult.AvailableClass> classes = new ArrayList<>();
            List<SeatAvailability> seatAvailabilities = seatAvailabilityRepository.findBySchedule(schedule);
            for (SeatAvailability sa : seatAvailabilities) {
                classes.add(TrainSearchResult.AvailableClass.builder()
                        .seatClass(sa.getSeatClass().name())
                        .availableSeats(sa.getAvailableSeats())
                        .fare(sa.getFare())
                        .build());
            }

            results.add(TrainSearchResult.builder()
                    .trainId(train.getId())
                    .trainNumber(train.getTrainNumber())
                    .trainName(train.getTrainName())
                    .operatorName(train.getOperatorName())
                    .originStation(train.getOriginStation())
                    .destinationStation(train.getDestinationStation())
                    .totalSeats(train.getTotalSeats())
                    .journeyDate(schedule.getScheduledDate())
                    .scheduledDeparture(schedule.getScheduledDeparture())
                    .scheduledArrival(schedule.getScheduledArrival())
                    .delayMinutes(schedule.getDelayMinutes())
                    .platform(schedule.getPlatform())
                    .cancelled(schedule.isCancelled())
                    .availableClasses(classes)
                    .build());
        }

        log.debug("Search {} -> {} on {} returned {} results",
                trimmedOrigin, trimmedDestination, journeyDate, results.size());
        return results;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Admin write operations
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public TrainResponse createTrain(CreateTrainRequest request) {
        String origin = request.getOriginStation().trim();
        String destination = request.getDestinationStation().trim();

        if (origin.equalsIgnoreCase(destination)) {
            throw new BadRequestException(
                    "Origin and destination stations must be different.",
                    ErrorCode.INVALID_OPERATION);
        }

        String trainNumber = request.getTrainNumber().trim();
        if (trainRepository.existsByTrainNumber(trainNumber)) {
            throw new DuplicateResourceException("Train", "trainNumber", trainNumber);
        }

        Train train = Train.builder()
                .trainNumber(trainNumber)
                .trainName(request.getTrainName().trim())
                .operatorName(StringUtils.hasText(request.getOperatorName())
                        ? request.getOperatorName().trim() : null)
                .originStation(origin)
                .destinationStation(destination)
                .totalSeats(request.getTotalSeats())
                .active(true)
                .build();

        Train saved = trainRepository.save(train);
        log.info("Created new train: {} ({})", saved.getTrainNumber(), saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public TrainResponse updateTrain(Long id, UpdateTrainRequest request) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train", "id", id));

        if (StringUtils.hasText(request.getTrainName())) {
            train.setTrainName(request.getTrainName().trim());
        }

        if (request.getOperatorName() != null) {
            train.setOperatorName(request.getOperatorName().trim().isEmpty()
                    ? null : request.getOperatorName().trim());
        }

        boolean originChanged = false;
        boolean destinationChanged = false;
        String newOrigin = train.getOriginStation();
        String newDestination = train.getDestinationStation();

        if (StringUtils.hasText(request.getOriginStation())) {
            newOrigin = request.getOriginStation().trim();
            originChanged = true;
        }
        if (StringUtils.hasText(request.getDestinationStation())) {
            newDestination = request.getDestinationStation().trim();
            destinationChanged = true;
        }
        if (originChanged || destinationChanged) {
            if (newOrigin.equalsIgnoreCase(newDestination)) {
                throw new BadRequestException(
                        "Origin and destination stations must be different.",
                        ErrorCode.INVALID_OPERATION);
            }
            train.setOriginStation(newOrigin);
            train.setDestinationStation(newDestination);
        }

        if (request.getTotalSeats() != null) {
            train.setTotalSeats(request.getTotalSeats());
        }

        if (request.getActive() != null) {
            train.setActive(request.getActive());
        }

        Train updated = trainRepository.save(train);
        log.info("Updated train id={}, number={}", updated.getId(), updated.getTrainNumber());
        return toResponse(updated);
    }

    @Transactional
    public TrainResponse deactivateTrain(Long id) {
        Train train = trainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Train", "id", id));

        if (!train.isActive()) {
            log.warn("Train id={} is already inactive — deactivate is a no-op.", id);
        }

        train.setActive(false);
        Train saved = trainRepository.save(train);
        log.info("Deactivated train id={}, number={}", saved.getId(), saved.getTrainNumber());
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private Sort resolveSort(String sortBy, String sortDir) {
        String property = switch (sortBy == null ? "" : sortBy.toLowerCase()) {
            case "name", "trainname" -> "trainName";
            case "number", "trainnumber" -> "trainNumber";
            case "operator", "operatorname" -> "operatorName";
            case "origin" -> "originStation";
            case "destination" -> "destinationStation";
            case "seats", "totalseats" -> "totalSeats";
            case "created", "createdat" -> "createdAt";
            case "updated", "updatedat" -> "updatedAt";
            default -> "trainNumber";
        };
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }

    private TrainResponse toResponse(Train train) {
        return TrainResponse.builder()
                .id(train.getId())
                .trainNumber(train.getTrainNumber())
                .trainName(train.getTrainName())
                .operatorName(train.getOperatorName())
                .originStation(train.getOriginStation())
                .destinationStation(train.getDestinationStation())
                .totalSeats(train.getTotalSeats())
                .active(train.isActive())
                .createdAt(train.getCreatedAt())
                .updatedAt(train.getUpdatedAt())
                .build();
    }

    private PaginatedTrainResponse toPaginatedResponse(Page<Train> page) {
        List<TrainResponse> responses = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return PaginatedTrainResponse.builder()
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
