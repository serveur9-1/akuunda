package org.akuunda.akuundawallet.wallet.repository;

import org.akuunda.akuundawallet.wallet.api.entities.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByHotelIdAndIsAvailableTrue(Long hotelId);

    @Query("SELECT r FROM Room r WHERE r.hotel.id = :hotelId AND r.isAvailable = true AND r.maxGuests >= :guests")
    List<Room> findAvailableRooms(@Param("hotelId") Long hotelId, @Param("guests") Integer guests);
}
