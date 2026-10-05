package com.trainconcierge.simulation;

import com.trainconcierge.train.TrainStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulatedStatusHistoryEntry {

    private Long id;
    private TrainStatus status;
    private Integer delayMinutes;
    private String platform;
    private String message;
    private String recordedAtStation;
    private Instant recordedAt;
    private boolean simulated;
}
