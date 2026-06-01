package org.akuunda.akuundawallet.wallet.repository;

import org.akuunda.akuundawallet.wallet.api.entities.TransportProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransportProviderRepository extends JpaRepository<TransportProvider, Long> {

    Page<TransportProvider> findByIsAvailableTrue(Pageable pageable);

    Page<TransportProvider> findByCityIgnoreCaseAndIsAvailableTrue(String city, Pageable pageable);

    @Query("SELECT t FROM TransportProvider t WHERE t.isAvailable = true AND t.serviceTypes LIKE %:type%")
    Page<TransportProvider> findByServiceType(@Param("type") String type, Pageable pageable);

    @Query("SELECT t FROM TransportProvider t WHERE t.owner.username = :username")
    List<TransportProvider> findByOwnerUsername(@Param("username") String username);

    Optional<TransportProvider> findByIdAndIsAvailableTrue(Long id);
}
