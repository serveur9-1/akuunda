package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.UserPin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPinRepository extends JpaRepository<UserPin, String> {

    Optional<UserPin> findTopByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Trouve tous les PINs d'un utilisateur
     */
    List<UserPin> findByUserId(String userId);
}


