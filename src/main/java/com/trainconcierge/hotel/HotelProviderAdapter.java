package com.trainconcierge.hotel;

import java.time.LocalDate;

public interface HotelProviderAdapter {

    SimulatedHotelResult confirmReservation(HotelBooking booking);

    SimulatedHotelResult reschedule(HotelBooking booking,
                                     LocalDate oldCheckIn, LocalDate oldCheckOut,
                                     LocalDate newCheckIn, LocalDate newCheckOut);
}
