package com.trainconcierge.cab;

import com.trainconcierge.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "cab_modification_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CabModificationAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cab_booking_id", nullable = false)
    private CabBooking cabBooking;

    @Column(nullable = false)
    private LocalDateTime oldPickupTime;

    @Column(nullable = false)
    private LocalDateTime newPickupTime;

    @Column(nullable = false)
    private Instant rescheduledAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String simulationProvider = "MOCK";
}
