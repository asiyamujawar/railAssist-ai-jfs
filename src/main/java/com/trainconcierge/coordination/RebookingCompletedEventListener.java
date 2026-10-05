package com.trainconcierge.coordination;

import com.trainconcierge.rebooking.RebookingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Spring Event Listener that automatically triggers travel coordination after
 * a train rebooking transaction completes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RebookingCompletedEventListener {

    private final TravelCoordinationService travelCoordinationService;

    /**
     * Handles {@link RebookingCompletedEvent} by initiating post-rebooking travel coordination.
     *
     * @param event the rebooking completion event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRebookingCompleted(RebookingCompletedEvent event) {
        Long historyId = event.getRebookingHistory().getId();
        log.info("[EventListener] RebookingCompletedEvent received for rebookingHistoryId={}. Triggering travel coordination...", historyId);
        try {
            travelCoordinationService.coordinateTravelForRebooking(historyId);
        } catch (Exception e) {
            log.error("[EventListener] Failed to execute travel coordination for rebookingHistoryId={}: {}", historyId, e.getMessage());
        }
    }
}
