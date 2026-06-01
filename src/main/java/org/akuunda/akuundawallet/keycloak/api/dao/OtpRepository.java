package org.akuunda.akuundawallet.keycloak.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Otp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface OtpRepository extends JpaRepository<Otp,String> {

    // Legacy — kept for compatibility, do not use for verification
    Otp getOtpByUserNameAndOtpCode(String userName, String otp);

    /**
     * Retourne le plus récent OTP en attente (ATTENTE) pour un user + code donné.
     * Évite de matcher un OTP déjà VALIDATED (ex: WhatsApp vérifié avant Email).
     */
    @Query("SELECT o FROM Otp o WHERE o.userName = :userName AND o.otpCode = :otpCode AND o.status = 'ATTENTE' ORDER BY o.dateCreate DESC")
    Optional<Otp> findLatestPendingOtp(@Param("userName") String userName, @Param("otpCode") String otpCode);
}
