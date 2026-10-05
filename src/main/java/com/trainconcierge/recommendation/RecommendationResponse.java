package com.trainconcierge.recommendation;

import com.trainconcierge.schedule.TrainSchedule;
import com.trainconcierge.train.Train;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Response DTO for alternative train recommendations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private Long id;
    private Long disruptionEventId;
    private Long suggestedScheduleId;
    private String trainNumber;
    private String trainName;
    private String originStation;
    private String destinationStation;
    private LocalDate scheduledDate;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private BigDecimal estimatedFare;
    private Integer availableSeats;
    private double score;
    private String reason;
    private String status;

    public static RecommendationResponse fromEntity(Recommendation recommendation, int availableSeats) {
        TrainSchedule sched = recommendation.getSuggestedSchedule();
        Train train = sched != null ? sched.getTrain() : null;

        double rawScore = recommendation.getScore() != null ? recommendation.getScore().doubleValue() : 0.0;
        double scoreVal = rawScore <= 1.0 ? BigDecimal.valueOf(rawScore * 100.0).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue() : rawScore;

        return RecommendationResponse.builder()
                .id(recommendation.getId())
                .disruptionEventId(recommendation.getDisruptionEvent() != null ? recommendation.getDisruptionEvent().getId() : null)
                .suggestedScheduleId(sched != null ? sched.getId() : null)
                .trainNumber(train != null ? train.getTrainNumber() : "N/A")
                .trainName(train != null ? train.getTrainName() : "N/A")
                .originStation(train != null ? train.getOriginStation() : null)
                .destinationStation(train != null ? train.getDestinationStation() : null)
                .scheduledDate(sched != null ? sched.getScheduledDate() : null)
                .departureTime(sched != null ? sched.getScheduledDeparture() : null)
                .arrivalTime(sched != null ? sched.getScheduledArrival() : null)
                .estimatedFare(recommendation.getEstimatedFare())
                .availableSeats(availableSeats)
                .score(scoreVal)
                .reason(recommendation.getReason())
                .status(recommendation.getStatus())
                .build();
    }
}
