package com.trainconcierge.rebooking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Publishes {@link RebookingCompletedEvent} after a successful rebooking.
 *
 * <p><strong>Design Constraint:</strong> This service does NOT perform hotel,
 * cab, or notification updates directly. It simply publishes a Spring
 * application event that downstream listeners can consume.</p>
 *
 * <p>Future listeners could include:</p>
 * <ul>
 *   <li>HotelRescheduleListener — reschedules hotel stays to match new arrival</li>
 *   <li>CabRescheduleListener — updates pickup time/location</li>
 *   <li>NotificationListener — sends SMS/email confirmation to passenger</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostRebookingWorkflowTrigger {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Publishes a {@link RebookingCompletedEvent} for downstream consumption.
     *
     * @param history the completed rebooking history record
     */
    public void triggerPostRebookingWorkflow(RebookingHistory history) {
        log.info("[PostRebooking] Publishing RebookingCompletedEvent for " +
                 "rebookingHistoryId={} journeyId={} originalBookingId={} newBookingId={}",
                history.getId(),
                history.getJourney() != null ? history.getJourney().getId() : null,
                history.getOriginalBooking().getId(),
                history.getNewBooking() != null ? history.getNewBooking().getId() : null);

        eventPublisher.publishEvent(new RebookingCompletedEvent(this, history));
    }
}
