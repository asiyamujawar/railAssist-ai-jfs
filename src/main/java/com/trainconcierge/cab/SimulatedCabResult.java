package com.trainconcierge.cab;

public record SimulatedCabResult(
        boolean success,
        String externalBookingRef,
        String message
) {
}
