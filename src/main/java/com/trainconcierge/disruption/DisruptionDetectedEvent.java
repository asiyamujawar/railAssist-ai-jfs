package com.trainconcierge.disruption;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Spring ApplicationEvent published when an eligible disruption event is detected.
 */
@Getter
public class DisruptionDetectedEvent extends ApplicationEvent {

    private final DisruptionEvent disruptionEvent;

    public DisruptionDetectedEvent(Object source, DisruptionEvent disruptionEvent) {
        super(source);
        this.disruptionEvent = disruptionEvent;
    }
}
