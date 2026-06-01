package org.akuunda.akuundawallet.keycloak.impl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.utils.StatusTypeEnum;
import org.akuunda.akuundawallet.keycloak.api.dao.OtpRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Otp;
import org.akuunda.akuundawallet.keycloak.api.service.OtpService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private final OtpRepository otpRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean verifyOtp(String otpCode, String userName) {
        // Use the query that filters by status=ATTENTE and picks the most recent OTP.
        // This prevents matching a previously VALIDATED OTP (e.g. WhatsApp already verified
        // when the user is now verifying the Email OTP with the same or different code).
        Optional<Otp> otpOpt = otpRepository.findLatestPendingOtp(userName, otpCode);

        if (otpOpt.isEmpty()) {
            log.info("No pending OTP found for user {} with provided code", userName);
            return false;
        }

        Otp otp = otpOpt.get();
        final var now = Timestamp.from(Instant.now());

        if (otp.getExpiredDate().before(now)) {
            log.info("OTP expired for user {}", userName);
            return false;
        }

        log.info("OTP valid — marking as VALIDATED for user {}", userName);
        otp.setStatus(StatusTypeEnum.VALIDATED.toString());
        otp.setDateValidated(now);
        otpRepository.saveAndFlush(otp);
        return true;
    }
}
