package com.trainconcierge.cab.dto;

import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabModificationResponse {

    private LocalDateTime oldPickupTime;
    private LocalDateTime newPickupTime;
    private Instant rescheduledAt;
    private String simulationProvider;
}
