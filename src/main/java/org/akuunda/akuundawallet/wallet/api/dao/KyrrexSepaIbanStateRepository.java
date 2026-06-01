package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.KyrrexSepaIbanState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KyrrexSepaIbanStateRepository extends JpaRepository<KyrrexSepaIbanState, Long> {

    Optional<KyrrexSepaIbanState> findTopByUsernameAndProviderIdAndInstrumentOrderByUpdatedAtDesc(
            String username, String providerId, String instrument
    );
}
