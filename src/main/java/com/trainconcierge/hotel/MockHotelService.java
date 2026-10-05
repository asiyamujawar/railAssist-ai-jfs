package com.trainconcierge.hotel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Locale;

@Slf4j
@Service
public class MockHotelService implements HotelProviderAdapter {

    private static final int REF_LENGTH = 8;
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String SIM_REF_PREFIX = "SIM-HOTEL-";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public SimulatedHotelResult confirmReservation(HotelBooking booking) {
        String simulatedRef = SIM_REF_PREFIX + randomAlnum();
        log.info("MockHotelService: simulated confirm for user={} hotel={} ref={} (NO real hotel contacted)",
                booking.getUser() != null ? booking.getUser().getId() : "?",
                booking.getHotelName(),
                simulatedRef);
        return new SimulatedHotelResult(
                true,
                simulatedRef,
                "This reservation was simulated locally. No real hotel provider was contacted."
        );
    }

    @Override
    public SimulatedHotelResult reschedule(HotelBooking booking,
                                            LocalDate oldCheckIn, LocalDate oldCheckOut,
                                            LocalDate newCheckIn, LocalDate newCheckOut) {
        String simulatedRef = SIM_REF_PREFIX + randomAlnum();
        log.info("MockHotelService: simulated reschedule for ref={} {}->{} and {}->{} (NO real hotel contacted)",
                booking.getHotelBookingReference(),
                oldCheckIn, newCheckIn, oldCheckOut, newCheckOut);
        return new SimulatedHotelResult(
                true,
                simulatedRef,
                "The reschedule was simulated locally. No real hotel provider was contacted."
        );
    }

    private String randomAlnum() {
        StringBuilder sb = new StringBuilder(REF_LENGTH);
        for (int i = 0; i < REF_LENGTH; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }
}
