package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, Long> {

    // JOIN FETCH sur merchant (+ countryCurrency) et permanentLink (+ creator + countryCurrency) :
    // évite 2-4 SELECT secondaires par clé à cause des associations EAGER.
    @Query("SELECT k FROM MerchantApiKey k " +
           "LEFT JOIN FETCH k.merchant m LEFT JOIN FETCH m.countryCurrency " +
           "LEFT JOIN FETCH k.permanentLink pl LEFT JOIN FETCH pl.creator pc LEFT JOIN FETCH pc.countryCurrency " +
           "WHERE k.apiKey = :apiKey AND k.isActive = true")
    Optional<MerchantApiKey> findByApiKeyAndIsActiveTrue(@Param("apiKey") String apiKey);

    @Query("SELECT k FROM MerchantApiKey k " +
           "LEFT JOIN FETCH k.merchant m LEFT JOIN FETCH m.countryCurrency " +
           "LEFT JOIN FETCH k.permanentLink pl LEFT JOIN FETCH pl.creator pc LEFT JOIN FETCH pc.countryCurrency " +
           "WHERE m = :merchant ORDER BY k.createdAt DESC")
    List<MerchantApiKey> findByMerchantOrderByCreatedAtDesc(@Param("merchant") Users merchant);

    @Query("SELECT k FROM MerchantApiKey k " +
           "LEFT JOIN FETCH k.merchant m LEFT JOIN FETCH m.countryCurrency " +
           "LEFT JOIN FETCH k.permanentLink pl LEFT JOIN FETCH pl.creator pc LEFT JOIN FETCH pc.countryCurrency " +
           "WHERE k.apiKey = :apiKey")
    Optional<MerchantApiKey> findByApiKey(@Param("apiKey") String apiKey);
}
