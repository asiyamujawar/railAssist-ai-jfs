package com.trainconcierge.disruption;

import com.trainconcierge.exception.ForbiddenException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.user.User;
import com.trainconcierge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying disruption events with user ownership access control.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DisruptionQueryService {

    private final DisruptionEventRepository disruptionEventRepository;
    private final UserRepository userRepository;

    /**
     * Retrieves all disruption events belonging to the currently authenticated user's journeys.
     *
     * @param email the authenticated user's email
     * @return list of disruption event responses
     */
    public List<DisruptionEventResponse> getMyDisruptions(String email) {
        User currentUser = loadUser(email);
        log.debug("[DisruptionQuery] Fetching disruptions for user email={} (id={})", email, currentUser.getId());
        List<DisruptionEvent> events = disruptionEventRepository
                .findByJourneyUserIdOrderByDetectedAtDesc(currentUser.getId());

        return events.stream()
                .map(DisruptionEventResponse::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a specific disruption event by ID, enforcing user ownership.
     *
     * @param id the disruption event ID
     * @param email the authenticated user's email
     * @return disruption event response
     */
    public DisruptionEventResponse getDisruptionById(Long id, String email) {
        User currentUser = loadUser(email);
        log.debug("[DisruptionQuery] Fetching disruption id={} for user email={} (id={})", id, email, currentUser.getId());
        DisruptionEvent event = disruptionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DisruptionEvent", "id", id));

        // Enforce user ownership access control
        boolean isOwner = event.getJourney() != null
                && event.getJourney().getUser() != null
                && event.getJourney().getUser().getId().equals(currentUser.getId());

        boolean isAdmin = currentUser.getRole() != null
                && currentUser.getRole().name().equals("ROLE_ADMIN");

        if (!isOwner && !isAdmin) {
            log.warn("[DisruptionQuery] Access denied for user id={} attempting to view disruption id={}",
                    currentUser.getId(), id);
            throw new ForbiddenException("Access denied: You can only retrieve disruptions belonging to your own journeys.");
        }

        return DisruptionEventResponse.fromEntity(event);
    }

    private User loadUser(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }
}
