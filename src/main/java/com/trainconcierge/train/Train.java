package com.trainconcierge.train;

import com.trainconcierge.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a physical train and its static attributes.
 *
 * trainNumber is the unique operational identifier (e.g. "12301", "LNER123").
 *
 * Relationships:
 * - One Train → many TrainSchedules
 * - One Train → many TrainStatusHistory records
 * - One Train → many SeatAvailability records
 */
@Entity
@Table(
    name = "trains",
    uniqueConstraints = @UniqueConstraint(name = "uq_train_number", columnNames = "trainNumber")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Train extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String trainNumber;

    @Column(nullable = false, length = 150)
    private String trainName;

    /** Operating company / franchise name. */
    @Column(length = 100)
    private String operatorName;

    /** Origin station name. */
    @Column(nullable = false, length = 100)
    private String originStation;

    /** Destination station name. */
    @Column(nullable = false, length = 100)
    private String destinationStation;

    /** Total number of seats across all classes. */
    @Column(nullable = false)
    private Integer totalSeats;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
