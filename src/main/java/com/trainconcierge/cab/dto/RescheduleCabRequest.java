package com.trainconcierge.cab.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleCabRequest {

    @NotNull(message = "newScheduledPickupTime is required")
    private LocalDateTime newScheduledPickupTime;
}
