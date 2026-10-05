package com.trainconcierge.rebooking;

import org.springframework.context.ApplicationEvent;

/**
 * Spring application event published after a rebooking transaction commits.
 *
 * <p>Downstream listeners (hotel/cab rescheduling, notification service)
 * subscribe to this event to trigger post-rebooking workflows without
 * coupling the rebooking service to those domains.</p>
 */
public class RebookingCompletedEvent extends ApplicationEvent {

    private final RebookingHistory rebookingHistory;

    public RebookingCompletedEvent(Object source, RebookingHistory rebookingHistory) {
        super(source);
        this.rebookingHistory = rebookingHistory;
    }

    public RebookingHistory getRebookingHistory() {
        return rebookingHistory;
    }
}
