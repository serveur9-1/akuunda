package org.akuunda.akuundawallet.keycloak.impl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.AfricanDto;
import org.akuunda.akuundawallet.keycloak.api.service.SmsService;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.AkunndaMTargetSmsClientService;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.AkuundaInfoBipClientService;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.WhatsappClientService;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class SmsServiceImpl implements SmsService {

    private static final String AFRICA = "Afrique";

    private final AkunndaMTargetSmsClientService mTargetSmsClientService;
    private final AkuundaInfoBipClientService akuundaInfoBipClientService;
    private final WhatsappClientService whatsappClientService;
    private final CountryCurrencyRepository currencyRepository;

    @Override
    public ResponseEntity<String> sendSms(String message, List<String> listMsisdn, String type) {
        List<String> responses = new ArrayList<>();

        if (listMsisdn.isEmpty()) {
            return new ResponseEntity<>("List of msisdn is empty", HttpStatus.BAD_REQUEST);
        }

        if (type.equals("SMS")) {
            log.info("Sending SMS to list of msisdn: {}", listMsisdn);
            log.debug("Sending SMS to list of msisdn: {}", listMsisdn);
            // verifier si le numéro de téléphone est europeen ou africain
            listMsisdn.forEach(msisdn -> {
                if (Objects.requireNonNull(isAfrican(msisdn).getBody()).isAfrican()) {
                    log.info("msisdn {} is African", msisdn);
                    log.debug("msisdn {} is African", msisdn);
                    final var response = mTargetSmsClientService.SendSimpleSms(message, msisdn);
                    responses.add(response.getBody());
                } else {
                    log.info("msisdn {} is not African", msisdn);
                    log.debug("msisdn {} is not African", msisdn);
                    final var response = akuundaInfoBipClientService.SendSimpleSms(message, msisdn);
                    responses.add(response.getBody());
                }
            });
        } else {
            log.info("Sending WHATSAPP to list of msisdn: {}", listMsisdn);
            log.debug("Sending WHATSAPP to list of msisdn: {}", listMsisdn);
            listMsisdn.forEach(msisdn -> {
                final var response = whatsappClientService.SendSimpleSms(message, msisdn);
                responses.add(response.getBody());
            });
        }
        return new ResponseEntity<>(responses.toString(), HttpStatus.OK);
    }


    @Override
    public ResponseEntity<AfricanDto> isAfrican(final String mssIdn) {
        log.info("Checking if msisdn is African: {}", mssIdn);
        log.debug("Checking if msisdn is African: {}", mssIdn);

        final var listAfricanCountries = currencyRepository.findCountryCurrencyByContinentName(AFRICA);
        if (listAfricanCountries.isEmpty()) {
            log.info("No African countries found in the database");
            return new ResponseEntity<>(new AfricanDto(mssIdn, false), HttpStatus.NOT_FOUND);
        }
        // Check if the msisdn is in the list of African countries
        final var isAfrican = listAfricanCountries.stream()
                .anyMatch(country -> mssIdn
                        .substring(2)
                        .startsWith(
                                String.valueOf(country.getCallingCode())
                        ));
        if (isAfrican) {
            log.info("msisdn {} is African", mssIdn);
            log.debug("msisdn {} is African", mssIdn);
            return new ResponseEntity<>(new AfricanDto(mssIdn, true), HttpStatus.OK);
        } else {
            log.info("msisdn {} is not African", mssIdn);
            log.debug("msisdn {} is not African", mssIdn);
            return new ResponseEntity<>(new AfricanDto(mssIdn, false), HttpStatus.OK);
        }

    }
}
