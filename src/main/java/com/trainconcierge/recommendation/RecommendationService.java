package com.trainconcierge.recommendation;

import com.trainconcierge.disruption.DisruptionEvent;
import com.trainconcierge.disruption.DisruptionEventRepository;
import com.trainconcierge.disruption.DisruptionStatus;
import com.trainconcierge.exception.BadRequestException;
import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.schedule.ScheduleStatus;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for generating, ranking, and querying alternative train recommendations.
 *
 * <p><strong>Design Constraints:</strong> Uses Java-based deterministic weighted
 * scoring with zero machine learning or external LLM dependencies. Pure recommendation
 * calculation — does NOT perform booking, hotel, or cab updates.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final DisruptionEventRepository disruptionEventRepository;
    private final TrainScheduleRepository scheduleRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;
    private final RecommendationScorer scorer;
    private final RecommendationProperties properties;

    /**
     * Generates and ranks alternative train recommendations for a disruption event.
     *
     * @param disruptionId ID of the disruption event
     * @param userEmail email of the requesting user (for authorization check)
     * @return list of ranked recommendation responses (highest score first)
     */
    @Transactional
    public List<RecommendationResponse> generateRecommendations(Long disruptionId, String userEmail) {
        User currentUser = loadUser(userEmail);
        DisruptionEvent disruption = loadDisruptionAndCheckAccess(disruptionId, currentUser);

        TrainSchedule origSchedule = disruption.getSchedule();
        if (origSchedule == null) {
            throw new BadRequestException("No original train schedule associated with disruption id: " + disruptionId);
        }

        String origin = origSchedule.getTrain() != null ? origSchedule.getTrain().getOriginStation() : null;
        String dest = origSchedule.getTrain() != null ? origSchedule.getTrain().getDestinationStation() : null;

        if (origin == null || dest == null) {
            log.warn("[RecommendationEngine] Route origin/destination missing for schedule id={}", origSchedule.getId());
            return List.of();
        }

        log.info("[RecommendationEngine] Searching alternative schedules for disruption id={} route: {} -> {}",
                disruptionId, origin, dest);

        // 1. Search candidate schedules serving the same route
        List<TrainSchedule> candidates = scheduleRepository.findAll().stream()
                .filter(s -> s.getTrain() != null
                        && origin.equalsIgnoreCase(s.getTrain().getOriginStation())
                        && dest.equalsIgnoreCase(s.getTrain().getDestinationStation()))
                .collect(Collectors.toList());

        List<RecommendationCandidate> scoredCandidates = new ArrayList<>();

        for (TrainSchedule cand : candidates) {
            // 2. Exclude original schedule
            if (cand.getId().equals(origSchedule.getId())) {
                continue;
            }

            // 3. Exclude cancelled, departed, or ineligible alternatives
            if (cand.isCancelled() || cand.getScheduleStatus() == ScheduleStatus.CANCELLED) {
                log.debug("[RecommendationEngine] Candidate schedule id={} excluded: CANCELLED", cand.getId());
                continue;
            }

            // 4. Check seat availability across seat classes
            List<SeatAvailability> availabilities = seatAvailabilityRepository.findBySchedule(cand);
            int totalSeatsAvailable = availabilities.stream()
                    .mapToInt(SeatAvailability::getAvailableSeats)
                    .sum();

            if (totalSeatsAvailable < properties.getMinSeatsAvailable()) {
                log.debug("[RecommendationEngine] Candidate schedule id={} excluded: insufficient seats ({})",
                        cand.getId(), totalSeatsAvailable);
                continue;
            }

            // 5. Score candidate using deterministic weighted scorer
            RecommendationScorer.ScoreResult scoreResult = scorer.score(cand, origSchedule, totalSeatsAvailable);

            scoredCandidates.add(new RecommendationCandidate(cand, totalSeatsAvailable, scoreResult));
        }

        // Handle no eligible alternatives case cleanly
        if (scoredCandidates.isEmpty()) {
            log.info("[RecommendationEngine] No eligible alternative train schedules found for disruption id={}", disruptionId);
            return List.of();
        }

        // 6. Rank candidates by score descending
        scoredCandidates.sort(Comparator.comparingDouble((RecommendationCandidate rc) -> rc.scoreResult().totalScore100()).reversed());

        // 7. Clear existing pending recommendations for this disruption & user
        User targetUser = disruption.getJourney() != null ? disruption.getJourney().getUser() : currentUser;
        List<Recommendation> existing = recommendationRepository.findByDisruptionEventAndUser(disruption, targetUser);
        if (!existing.isEmpty()) {
            recommendationRepository.deleteAll(existing);
        }

        // 8. Persist ranked recommendations
        List<RecommendationResponse> responses = new ArrayList<>();
        for (RecommendationCandidate rc : scoredCandidates) {
            Recommendation rec = Recommendation.builder()
                    .disruptionEvent(disruption)
                    .user(targetUser)
                    .suggestedSchedule(rc.schedule())
                    .score(BigDecimal.valueOf(rc.scoreResult().normalizedScore()))
                    .estimatedFare(rc.schedule().getBaseFare())
                    .reason(rc.scoreResult().explanation())
                    .status("PENDING")
                    .build();

            Recommendation saved = recommendationRepository.save(rec);
            responses.add(RecommendationResponse.fromEntity(saved, rc.availableSeats()));
        }

        // Update disruption event status
        if (disruption.getStatus() == DisruptionStatus.DETECTED) {
            disruption.setStatus(DisruptionStatus.PROCESSING);
            disruption.setRebookingTriggered(true);
            disruptionEventRepository.save(disruption);
        }

        log.info("[RecommendationEngine] Successfully generated {} ranked recommendation(s) for disruption id={}",
                responses.size(), disruptionId);

        return responses;
    }

    /**
     * Retrieves existing generated recommendations for a disruption event.
     *
     * @param disruptionId ID of the disruption event
     * @param userEmail email of the requesting user
     * @return list of recommendation responses sorted by score descending
     */
    @Transactional(readOnly = true)
    public List<RecommendationResponse> getRecommendationsForDisruption(Long disruptionId, String userEmail) {
        User currentUser = loadUser(userEmail);
        DisruptionEvent disruption = loadDisruptionAndCheckAccess(disruptionId, currentUser);

        List<Recommendation> recs = recommendationRepository.findByDisruptionEventOrderByScoreDesc(disruption);

        return recs.stream().map(rec -> {
            List<SeatAvailability> availabilities = seatAvailabilityRepository.findBySchedule(rec.getSuggestedSchedule());
            int totalSeats = availabilities.stream().mapToInt(SeatAvailability::getAvailableSeats).sum();
            return RecommendationResponse.fromEntity(rec, totalSeats);
        }).collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Internal Helper Methods
    // ──────────────────────────────────────────────────────────────────────

    private User loadUser(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    private DisruptionEvent loadDisruptionAndCheckAccess(Long disruptionId, User currentUser) {
        DisruptionEvent disruption = disruptionEventRepository.findById(disruptionId)
                .orElseThrow(() -> new ResourceNotFoundException("DisruptionEvent", "id", disruptionId));

        boolean isOwner = disruption.getJourney() != null
                && disruption.getJourney().getUser() != null
                && disruption.getJourney().getUser().getId().equals(currentUser.getId());

        boolean isAdmin = currentUser.getRole() != null
                && currentUser.getRole().name().equals("ROLE_ADMIN");

        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Access denied: You can only access recommendations for your own journey disruptions.");
        }

        return disruption;
    }

    private record RecommendationCandidate(
            TrainSchedule schedule,
            int availableSeats,
            RecommendationScorer.ScoreResult scoreResult
    ) {}
}
