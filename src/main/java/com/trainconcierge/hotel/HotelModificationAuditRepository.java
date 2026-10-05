package com.trainconcierge.hotel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface HotelModificationAuditRepository
        extends JpaRepository<HotelModificationAudit, Long> {

    List<HotelModificationAudit> findByHotelBookingIn(Collection<HotelBooking> hotelBookings);
}
