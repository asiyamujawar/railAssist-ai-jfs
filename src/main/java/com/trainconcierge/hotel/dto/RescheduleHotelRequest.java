package com.trainconcierge.hotel.dto;

import lombok.*;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RescheduleHotelRequest {

    private LocalDate newCheckInDate;

    private LocalDate newCheckOutDate;
}
