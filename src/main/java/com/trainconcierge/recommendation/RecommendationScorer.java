package com.trainconcierge.recommendation;

import com.trainconcierge.schedule.TrainSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Deterministic weighted scorer for evaluating alternative train schedules.
 *
 * <p>Scores candidate schedules on a normalized 0–100 scale, outputting a 0.000–1.000
 * decimal score for persistence (fitting precision=4, scale=3 DB constraints).</p>
 */
@Component
@RequiredArgsConstructor
public class RecommendationScorer {

    private final RecommendationProperties properties;

    /**
     * Data class holding scoring breakdown results.
     */
    public record ScoreResult(
            double totalScore100, // 0 - 100 scale
            double normalizedScore, // 0.000 - 1.000 scale
            double arrivalScore,
            double durationScore,
            double fareScore,
            double seatsScore,
            String explanation
    ) {}

    /**
     * Calculates the deterministic score for a candidate schedule against an original schedule.
     *
     * @param candidate the alternative schedule being evaluated
     * @param original the disrupted original schedule
     * @param availableSeats available seats count on candidate schedule
     * @return ScoreResult containing sub-scores, total score, and explanation string
     */
    public ScoreResult score(TrainSchedule candidate, TrainSchedule original, int availableSeats) {
        // 1. Arrival Time Proximity Score (0 - 100)
        long origArrivalMin = original.getScheduledArrival().toSecondOfDay() / 60;
        long candArrivalMin = candidate.getScheduledArrival().toSecondOfDay() / 60;
        long arrivalDiffMin = Math.abs(candArrivalMin - origArrivalMin);

        double arrivalScore = 100.0;
        if (candArrivalMin > origArrivalMin) {
            arrivalScore = Math.max(0.0, 100.0 - (arrivalDiffMin * 0.5));
        }

        // 2. Travel Duration Score (0 - 100)
        long origDurationMin = Duration.between(original.getScheduledDeparture(), original.getScheduledArrival()).toMinutes();
        if (origDurationMin <= 0) origDurationMin += 24 * 60;

        long candDurationMin = Duration.between(candidate.getScheduledDeparture(), candidate.getScheduledArrival()).toMinutes();
        if (candDurationMin <= 0) candDurationMin += 24 * 60;

        double durationScore = 100.0;
        if (candDurationMin > origDurationMin) {
            long extraMin = candDurationMin - origDurationMin;
            durationScore = Math.max(0.0, 100.0 - (extraMin * 1.0));
        }

        // 3. Fare Difference Score (0 - 100)
        BigDecimal origFare = original.getBaseFare() != null ? original.getBaseFare() : BigDecimal.ZERO;
        BigDecimal candFare = candidate.getBaseFare() != null ? candidate.getBaseFare() : BigDecimal.ZERO;
        BigDecimal fareDiff = candFare.subtract(origFare);

        double fareScore = 100.0;
        if (fareDiff.compareTo(BigDecimal.ZERO) > 0) {
            double diffVal = fareDiff.doubleValue();
            fareScore = Math.max(0.0, 100.0 - (diffVal * 2.0));
        }

        // 4. Seat Availability Score (0 - 100)
        double seatsScore = Math.min(100.0, availableSeats * 2.0);

        // 5. Total Weighted Score (0 - 100)
        double wArrival = properties.getWeightArrival();
        double wDuration = properties.getWeightDuration();
        double wFare = properties.getWeightFare();
        double wSeats = properties.getWeightSeats();

        double totalScore100 = (arrivalScore * wArrival) +
                               (durationScore * wDuration) +
                               (fareScore * wFare) +
                               (seatsScore * wSeats);

        // Normalize to 0.000 - 1.000 scale for NUMERIC(4,3) persistence
        double normalizedScore = BigDecimal.valueOf(totalScore100 / 100.0)
                .setScale(3, RoundingMode.HALF_UP)
                .doubleValue();

        totalScore100 = BigDecimal.valueOf(totalScore100)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        // 6. Build Explanation Reason String
        String explanation = buildExplanation(candidate, original, arrivalDiffMin, candDurationMin, origDurationMin, fareDiff, availableSeats, totalScore100);

        return new ScoreResult(totalScore100, normalizedScore, arrivalScore, durationScore, fareScore, seatsScore, explanation);
    }

    private String buildExplanation(TrainSchedule candidate, TrainSchedule original,
                                     long arrivalDiffMin, long candDurationMin, long origDurationMin,
                                     BigDecimal fareDiff, int availableSeats, double totalScore100) {
        StringBuilder sb = new StringBuilder();

        String trainName = candidate.getTrain() != null ? candidate.getTrain().getTrainNumber() : "Alternative";
        sb.append(trainName).append(" departing at ").append(candidate.getScheduledDeparture())
                .append(" (arrives ").append(candidate.getScheduledArrival()).append("). ");

        if (arrivalDiffMin == 0) {
            sb.append("Same expected arrival time. ");
        } else if (candidate.getScheduledArrival().isBefore(original.getScheduledArrival())) {
            sb.append("Arrives ").append(arrivalDiffMin).append(" min earlier. ");
        } else {
            sb.append("Arrives ").append(arrivalDiffMin).append(" min later. ");
        }

        if (candDurationMin == origDurationMin) {
            sb.append("Same travel duration. ");
        } else if (candDurationMin < origDurationMin) {
            sb.append("Travel time ").append(candDurationMin).append("m (").append(origDurationMin - candDurationMin).append("m faster). ");
        } else {
            sb.append("Travel time ").append(candDurationMin).append("m (").append(candDurationMin - origDurationMin).append("m longer). ");
        }

        if (fareDiff.compareTo(BigDecimal.ZERO) == 0) {
            sb.append("No fare difference. ");
        } else if (fareDiff.compareTo(BigDecimal.ZERO) < 0) {
            sb.append("Fare is £").append(fareDiff.abs()).append(" cheaper. ");
        } else {
            sb.append("Fare difference: +£").append(fareDiff).append(". ");
        }

        sb.append(availableSeats).append(" available seat(s). ");
        sb.append("Score: ").append(totalScore100).append("/100.");

        return sb.toString();
    }
}
