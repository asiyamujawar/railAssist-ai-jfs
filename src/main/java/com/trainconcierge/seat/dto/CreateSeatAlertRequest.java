package com.trainconcierge.seat.dto;

import com.trainconcierge.seat.SeatClass;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSeatAlertRequest {

    @NotNull(message = "scheduleId is required")
    private Long scheduleId;

    @NotNull(message = "seatClass is required")
    private SeatClass seatClass;

    /**
     * Alert fires when availableSeats <= threshold.
     * Default 0 = alert only when at least 1 seat opens from fully booked.
     * Set to e.g. 5 to be alerted when 5 or fewer seats remain.
     */
    @Min(value = 0, message = "threshold must be 0 or greater")
    @Builder.Default
    private int threshold = 0;
}
