package com.trainconcierge.hotel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotelBookingReferenceGenerator {

    private static final String PREFIX = "HOTEL-";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int MAX_RETRIES = 100;

    private final AtomicLong counter = new AtomicLong(0L);
    private final HotelBookingRepository repository;

    public String generate() {
        String datePart = LocalDate.now().format(DATE_FMT);
        for (int i = 0; i < MAX_RETRIES; i++) {
            long n = counter.incrementAndGet();
            String candidate = PREFIX + datePart + "-" + String.format("%04X", n & 0xFFFF);
            if (!repository.existsByHotelBookingReference(candidate)) {
                return candidate;
            }
        }
        String fallback = PREFIX + datePart + "-" + Long.toHexString(System.nanoTime()).toUpperCase();
        log.warn("Hotel booking reference generator fell back to nanoTime value: {}", fallback);
        return fallback;
    }
}
