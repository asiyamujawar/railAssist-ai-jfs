package com.trainconcierge.cab;

import java.time.LocalDateTime;

public interface CabProviderAdapter {

    SimulatedCabResult confirmReservation(CabBooking booking);

    SimulatedCabResult reschedule(CabBooking booking,
                                  LocalDateTime oldPickupTime,
                                  LocalDateTime newPickupTime);
}
