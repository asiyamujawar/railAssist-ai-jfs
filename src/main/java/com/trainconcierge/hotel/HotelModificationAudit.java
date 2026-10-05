package com.trainconcierge.hotel;

import com.trainconcierge.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "hotel_modification_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelModificationAudit extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_booking_id", nullable = false)
    private HotelBooking hotelBooking;

    @Column(nullable = false)
    private LocalDate oldCheckInDate;

    @Column(nullable = false)
    private LocalDate newCheckInDate;

    @Column(nullable = false)
    private LocalDate oldCheckOutDate;

    @Column(nullable = false)
    private LocalDate newCheckOutDate;

    @Column(nullable = false)
    private Instant rescheduledAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String simulationProvider = "MOCK";
}
