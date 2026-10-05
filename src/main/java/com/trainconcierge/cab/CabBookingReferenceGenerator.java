package com.trainconcierge.cab;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class CabBookingReferenceGenerator {

    private static final String PREFIX = "CAB-";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final AtomicLong counter = new AtomicLong(System.currentTimeMillis() & 0xFFFFF);

    private final CabBookingRepository cabBookingRepository;

    public String generate() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);

        for (int i = 0; i < 100; i++) {
            long seq = counter.incrementAndGet() & 0xFFFFFF;
            String candidate = String.format("%s%s-%06X", PREFIX, datePart, seq);
            if (!cabBookingRepository.existsByCabBookingReference(candidate)) {
                return candidate;
            }
        }

        return String.format("%s%s-%08X", PREFIX, datePart, System.nanoTime() & 0xFFFFFFFFL);
    }
}
