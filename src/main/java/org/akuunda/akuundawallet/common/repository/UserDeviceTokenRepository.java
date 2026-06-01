package org.akuunda.akuundawallet.common.repository;

import org.akuunda.akuundawallet.common.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, Long> {

    /**
     * Retrouver tous les tokens FCM d'un utilisateur.
     */
    List<UserDeviceToken> findByUsername(String username);

    /**
     * Retrouver tous les tokens FCM de plusieurs utilisateurs (envoi en masse).
     */
    List<UserDeviceToken> findByUsernameIn(List<String> usernames);

    /**
     * Vérifier si un token existe déjà pour cet utilisateur.
     */
    Optional<UserDeviceToken> findByUsernameAndFcmToken(String username, String fcmToken);

    /**
     * Supprimer un token spécifique (déconnexion d'un device).
     */
    @Modifying
    @Query("DELETE FROM UserDeviceToken t WHERE t.username = :username AND t.fcmToken = :fcmToken")
    void deleteByUsernameAndFcmToken(@Param("username") String username, @Param("fcmToken") String fcmToken);

    /**
     * Supprimer tous les tokens d'un utilisateur (suppression de compte).
     */
    @Modifying
    void deleteAllByUsername(String username);

    /**
     * Supprimer un token invalide/expiré (appelé quand FCM retourne UNREGISTERED).
     */
    @Modifying
    void deleteByFcmToken(String fcmToken);
}
