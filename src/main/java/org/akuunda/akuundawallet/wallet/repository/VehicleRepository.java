package org.akuunda.akuundawallet.wallet.repository;

import org.akuunda.akuundawallet.wallet.api.entities.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByProviderIdAndIsAvailableTrue(Long providerId);
}
