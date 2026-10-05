package com.trainconcierge.config;

import com.trainconcierge.auth.AuthService;
import com.trainconcierge.auth.dto.RegisterRequest;
import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.schedule.TrainScheduleRepository;
import com.trainconcierge.seat.SeatAvailability;
import com.trainconcierge.seat.SeatAvailabilityRepository;
import com.trainconcierge.seat.SeatClass;
import com.trainconcierge.train.Train;
import com.trainconcierge.train.TrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Profile({"dev", "default"})
@RequiredArgsConstructor
public class TrainManagementDataLoader implements CommandLineRunner {

    private final TrainRepository trainRepository;
    private final TrainScheduleRepository trainScheduleRepository;
    private final SeatAvailabilityRepository seatAvailabilityRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public void run(String... args) {
        if (trainRepository.count() > 0) {
            log.debug("Train data already seeded (count={}) — skipping loader.", trainRepository.count());
            return;
        }

        log.info("═══════════════════════════════════════════════════════════");
        log.info("Phase 5: Seeding Train Management sample data...");
        log.info("═══════════════════════════════════════════════════════════");

        seedAdminUser();
        List<Train> trains = seedTrains();
        List<TrainSchedule> schedules = seedSchedules(trains);
        seedSeatAvailability(schedules);

        log.info("═══════════════════════════════════════════════════════════");
        log.info("Seeding complete: {} trains, {} schedules, {} seat-availability rows.",
                trainRepository.count(),
                trainScheduleRepository.count(),
                seatAvailabilityRepository.count());
        log.info("═══════════════════════════════════════════════════════════");
    }

    private void seedAdminUser() {
        try {
            RegisterRequest admin = RegisterRequest.builder()
                    .firstName("System")
                    .lastName("Administrator")
                    .email("admin@trainconcierge.dev")
                    .password("Admin@123")
                    .phoneNumber("+0000000000")
                    .build();
            authService.register(admin);
            log.info("  ▸ Seeded admin user: admin@trainconcierge.dev / Admin@123");
        } catch (Exception ex) {
            log.debug("Admin user already exists, skipping.");
        }

        try {
            RegisterRequest user = RegisterRequest.builder()
                    .firstName("Demo")
                    .lastName("Passenger")
                    .email("user@trainconcierge.dev")
                    .password("User@123")
                    .phoneNumber("+1111111111")
                    .build();
            authService.register(user);
            log.info("  ▸ Seeded demo user : user@trainconcierge.dev / User@123");
        } catch (Exception ex) {
            log.debug("Demo user already exists, skipping.");
        }
    }

    private List<Train> seedTrains() {
        List<Train> trains = new ArrayList<>();

        trains.add(createTrain("12001", "Shatabdi Express", "IRCTC",
                "New Delhi", "Kanpur", 600));
        trains.add(createTrain("12002", "Shatabdi Express Return", "IRCTC",
                "Kanpur", "New Delhi", 600));
        trains.add(createTrain("12301", "Howrah Rajdhani", "IRCTC",
                "Howrah", "New Delhi", 900));
        trains.add(createTrain("12302", "New Delhi Howrah Rajdhani", "IRCTC",
                "New Delhi", "Howrah", 900));
        trains.add(createTrain("22209", "Mumbai Central New Delhi Duronto", "IRCTC",
                "Mumbai Central", "New Delhi", 750));
        trains.add(createTrain("22210", "New Delhi Mumbai Central Duronto", "IRCTC",
                "New Delhi", "Mumbai Central", 750));
        trains.add(createTrain("12625", "Kerala Express", "IRCTC",
                "Thiruvananthapuram Central", "New Delhi", 1000));
        trains.add(createTrain("12626", "Kerala Express Return", "IRCTC",
                "New Delhi", "Thiruvananthapuram Central", 1000));
        trains.add(createTrain("12951", "Mumbai Rajdhani Express", "IRCTC",
                "Mumbai Central", "New Delhi", 850));
        trains.add(createTrain("12952", "Mumbai Rajdhani Return", "IRCTC",
                "New Delhi", "Mumbai Central", 850));
        trains.add(createTrain("17239", "Simhapuri Express", "IRCTC",
                "Secunderabad", "Gudur", 500));
        trains.add(createTrain("11077", "Jhelum Express", "IRCTC",
                "Pune", "Jammu Tawi", 950));
        trains.add(createTrain("20817", "Bhubaneswar New Delhi Duronto", "IRCTC",
                "Bhubaneswar", "New Delhi", 700));
        trains.add(createTrain("19223", "Ahmedabad Jammu Express", "IRCTC",
                "Ahmedabad", "Jammu Tawi", 600));
        trains.add(createTrain("12557", "Sapt Kranti Express", "IRCTC",
                "Muzaffarpur", "Anand Vihar", 800));

        return trainRepository.saveAll(trains);
    }

    private List<TrainSchedule> seedSchedules(List<Train> trains) {
        List<TrainSchedule> schedules = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Train train : trains) {
            for (int daysAhead = 0; daysAhead < 10; daysAhead++) {
                LocalDate date = today.plusDays(daysAhead);

                int totalSeats = train.getTotalSeats();
                LocalTime departure = pickDeparture(train);
                LocalTime arrival = departure.plusHours(4 + (train.getTotalSeats() % 7));

                TrainSchedule schedule = TrainSchedule.builder()
                        .train(train)
                        .scheduledDate(date)
                        .scheduledDeparture(departure)
                        .scheduledArrival(arrival)
                        .delayMinutes(0)
                        .platform(String.valueOf(1 + (daysAhead % 10)))
                        .cancelled(false)
                        .baseFare(BigDecimal.valueOf(500.00))
                        .build();

                schedules.add(schedule);
            }
        }

        return trainScheduleRepository.saveAll(schedules);
    }

    private void seedSeatAvailability(List<TrainSchedule> schedules) {
        List<SeatAvailability> allSeats = new ArrayList<>();

        for (TrainSchedule schedule : schedules) {
            Train train = schedule.getTrain();
            int firstClass = Math.max(1, (int) (train.getTotalSeats() * 0.10));
            int business = Math.max(1, (int) (train.getTotalSeats() * 0.15));
            int second = Math.max(1, (int) (train.getTotalSeats() * 0.45));
            int sleeper = Math.max(1, train.getTotalSeats() - firstClass - business - second);

            allSeats.add(buildSeatAvailability(schedule, SeatClass.FIRST, firstClass, BigDecimal.valueOf(1500.00)));
            allSeats.add(buildSeatAvailability(schedule, SeatClass.BUSINESS, business, BigDecimal.valueOf(950.00)));
            allSeats.add(buildSeatAvailability(schedule, SeatClass.SECOND, second, BigDecimal.valueOf(450.00)));
            allSeats.add(buildSeatAvailability(schedule, SeatClass.SLEEPER, sleeper, BigDecimal.valueOf(720.00)));
        }

        seatAvailabilityRepository.saveAll(allSeats);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private Train createTrain(String number, String name, String operator,
                              String origin, String destination, int seats) {
        Train t = Train.builder()
                .trainNumber(number)
                .trainName(name)
                .operatorName(operator)
                .originStation(origin)
                .destinationStation(destination)
                .totalSeats(seats)
                .active(true)
                .build();
        log.info("  ▸ Train {} — {} ({})", number, name, origin + " → " + destination);
        return t;
    }

    private LocalTime pickDeparture(Train train) {
        int bucket = Math.abs(train.getTrainNumber().hashCode()) % 5;
        return switch (bucket) {
            case 0 -> LocalTime.of(5, 30);
            case 1 -> LocalTime.of(8, 0);
            case 2 -> LocalTime.of(12, 15);
            case 3 -> LocalTime.of(17, 45);
            default -> LocalTime.of(21, 5);
        };
    }

    private SeatAvailability buildSeatAvailability(TrainSchedule schedule, SeatClass seatClass,
                                                    int total, BigDecimal farePerSeat) {
        int booked = (int) (Math.random() * total * 0.5);
        return SeatAvailability.builder()
                .schedule(schedule)
                .seatClass(seatClass)
                .totalSeats(total)
                .bookedSeats(booked)
                .availableSeats(total - booked)
                .fare(farePerSeat)
                .build();
    }
}
