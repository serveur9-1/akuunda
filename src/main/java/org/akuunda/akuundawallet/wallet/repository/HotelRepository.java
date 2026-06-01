package org.akuunda.akuundawallet.wallet.repository;

import org.akuunda.akuundawallet.wallet.api.entities.Hotel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    Page<Hotel> findByIsActiveTrue(Pageable pageable);

    Page<Hotel> findByCityIgnoreCaseAndIsActiveTrue(String city, Pageable pageable);

    Page<Hotel> findByCountryIgnoreCaseAndIsActiveTrue(String country, Pageable pageable);

    Page<Hotel> findByCityIgnoreCaseAndCountryIgnoreCaseAndIsActiveTrue(String city, String country, Pageable pageable);

    @Query("SELECT h FROM Hotel h WHERE h.owner.username = :username AND h.isActive = true")
    List<Hotel> findByOwnerUsername(@Param("username") String username);

    @Query("SELECT h FROM Hotel h WHERE h.isActive = true AND " +
           "(LOWER(h.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(h.city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(h.country) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Hotel> searchHotels(@Param("search") String search, Pageable pageable);

    Optional<Hotel> findByIdAndIsActiveTrue(Long id);
}
