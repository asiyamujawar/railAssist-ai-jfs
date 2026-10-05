package com.trainconcierge.cab;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Slf4j
@Service
public class MockCabService implements CabProviderAdapter {

    private static final int REF_LENGTH = 8;
    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String SIM_REF_PREFIX = "SIM-CAB-";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public SimulatedCabResult confirmReservation(CabBooking booking) {
        String simulatedRef = SIM_REF_PREFIX + randomAlnum();
        log.info("MockCabService: simulated confirm for user={} pickup={} dropoff={} ref={} (NO real cab provider contacted)",
                booking.getUser() != null ? booking.getUser().getId() : "?",
                booking.getPickupAddress(),
                booking.getDropoffAddress(),
                simulatedRef);
        return new SimulatedCabResult(
                true,
                simulatedRef,
                "This cab reservation was simulated locally. No real cab provider was contacted."
        );
    }

    @Override
    public SimulatedCabResult reschedule(CabBooking booking,
                                          LocalDateTime oldPickupTime,
                                          LocalDateTime newPickupTime) {
        String simulatedRef = SIM_REF_PREFIX + randomAlnum();
        log.info("MockCabService: simulated reschedule for ref={} {} -> {} (NO real cab provider contacted)",
                booking.getCabBookingReference(),
                oldPickupTime, newPickupTime);
        return new SimulatedCabResult(
                true,
                simulatedRef,
                "The cab reschedule was simulated locally. No real cab provider was contacted."
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
