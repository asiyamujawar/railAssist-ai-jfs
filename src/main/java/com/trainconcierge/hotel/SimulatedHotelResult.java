package com.trainconcierge.hotel;

public record SimulatedHotelResult(
        boolean simulated,
        String externalBookingRef,
        String notes
) {
}
