package org.akuunda.akuundawallet.wallet.repository;

import org.akuunda.akuundawallet.wallet.api.entities.Booking;
import org.akuunda.akuundawallet.wallet.api.enums.BookingStatus;
import org.akuunda.akuundawallet.wallet.api.enums.BookingType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByReference(String reference);

    Optional<Booking> findByConditionalPaymentId(Long conditionalPaymentId);

    Optional<Booking> findByConditionalPaymentCode(String conditionalPaymentCode);

    // Client bookings
    @Query("SELECT b FROM Booking b WHERE b.user.username = :username ORDER BY b.createdAt DESC")
    Page<Booking> findByUserUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.user.username = :username AND b.status = :status ORDER BY b.createdAt DESC")
    Page<Booking> findByUserUsernameAndStatus(@Param("username") String username, @Param("status") BookingStatus status, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.user.username = :username AND b.type = :type ORDER BY b.createdAt DESC")
    Page<Booking> findByUserUsernameAndType(@Param("username") String username, @Param("type") BookingType type, Pageable pageable);

    // Provider bookings
    @Query("SELECT b FROM Booking b WHERE b.providerUser.username = :username ORDER BY b.createdAt DESC")
    Page<Booking> findByProviderUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT b FROM Booking b WHERE b.providerUser.username = :username AND b.status = :status ORDER BY b.createdAt DESC")
    Page<Booking> findByProviderUsernameAndStatus(@Param("username") String username, @Param("status") BookingStatus status, Pageable pageable);

    // Expired bookings
    @Query("SELECT b FROM Booking b WHERE b.status = 'PENDING' AND b.expiresAt < :now")
    List<Booking> findExpiredBookings(@Param("now") LocalDateTime now);
}
