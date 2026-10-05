package com.trainconcierge.booking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingReferenceGenerator {

    private static final String PREFIX = "TC";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long RANDOM_MASK = 0xFFFFL;

    private final BookingRepository bookingRepository;

    private final AtomicLong counter = new AtomicLong(
            (System.currentTimeMillis() & RANDOM_MASK) << 4 | 0x1L);

    public String generate() {
        String date = LocalDate.now().format(DATE_FMT);
        for (int i = 0; i < 100; i++) {
            long seq = counter.incrementAndGet() & 0xFFFFFFFFL;
            String ref = String.format("%s-%s-%06X", PREFIX, date, seq);
            if (!bookingRepository.existsByBookingReference(ref)) {
                return ref;
            }
        }
        long rnd = System.nanoTime() & 0xFFFFFFFFL;
        return String.format("%s-%s-%08X", PREFIX, date, rnd);
    }
}
