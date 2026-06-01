package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.UserEmergencyCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserEmergencyCodeRepository extends JpaRepository<UserEmergencyCode, String> {

    Optional<UserEmergencyCode> findTopByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Trouve tous les codes d'urgence d'un utilisateur
     */
    List<UserEmergencyCode> findByUserId(String userId);
}
