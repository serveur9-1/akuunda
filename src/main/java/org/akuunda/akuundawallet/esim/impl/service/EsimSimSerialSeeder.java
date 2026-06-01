package org.akuunda.akuundawallet.esim.impl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.esim.api.dao.EsimSimSerialRepository;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerial;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(value = "esim.test.seed-enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class EsimSimSerialSeeder {

    private final EsimSimSerialRepository simSerialRepository;

    @Value("${esim.test.sim-serials:}")
    private String testSimSerials;

    @PostConstruct
    void seed() {
        if (testSimSerials == null || testSimSerials.isBlank()) {
            return;
        }
        for (String raw : testSimSerials.split(",")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            String serial = entry;
            String msisdn = null;
            int sepIndex = entry.indexOf(':');
            if (sepIndex > 0) {
                serial = entry.substring(0, sepIndex).trim();
                String msisdnPart = entry.substring(sepIndex + 1).trim();
                if (!msisdnPart.isEmpty()) {
                    msisdn = msisdnPart;
                }
            }
            if (serial.isEmpty()) {
                continue;
            }
            String finalSerial = serial;
            String finalMsisdn = msisdn;
            simSerialRepository.findBySimSerial(finalSerial)
                    .map(existing -> {
                        if (finalMsisdn != null && (existing.getMsisdn() == null || existing.getMsisdn().isBlank())) {
                            existing.setMsisdn(finalMsisdn);
                            log.info("Mise a jour msisdn de test pour simSerial {}.", finalSerial);
                            return simSerialRepository.save(existing);
                        }
                        return existing;
                    })
                    .orElseGet(() -> {
                        log.info("Ajout simSerial de test en base: {}", finalSerial);
                        return simSerialRepository.save(new EsimSimSerial(finalSerial, finalMsisdn));
                    });
        }
    }
}
