package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.wallet.api.dao.SavedPaymentAccountRepository;
import org.akuunda.akuundawallet.wallet.api.dto.SavedPaymentAccountPayload;
import org.akuunda.akuundawallet.wallet.api.entities.SavedPaymentAccount;
import org.akuunda.akuundawallet.wallet.service.SavedPaymentAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SavedPaymentAccountServiceImpl implements SavedPaymentAccountService {

    private final SavedPaymentAccountRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<SavedPaymentAccountPayload> listForUser(String username) {
        return repository.findByUsernameOrderByUpdatedAtDesc(username).stream()
                .map(this::toPayload)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SavedPaymentAccountPayload upsert(String username, SavedPaymentAccountPayload payload) {
        String resolvedId = payload.getId();
        if (resolvedId == null || resolvedId.isBlank()) {
            resolvedId = UUID.randomUUID().toString();
            payload.setId(resolvedId);
        }

        final String idForSave = resolvedId;

        SavedPaymentAccount entity = repository.findById(idForSave).orElse(null);
        if (entity != null && !username.equals(entity.getUsername())) {
            throw new IllegalArgumentException("Compte non trouvé pour cet utilisateur");
        }

        if (payload.isDefaultAccount()) {
            repository.findByUsernameOrderByUpdatedAtDesc(username).stream()
                    .filter(SavedPaymentAccount::isDefaultFlag)
                    .filter(a -> !a.getId().equals(idForSave))
                    .forEach(a -> {
                        a.setDefaultFlag(false);
                        repository.save(a);
                    });
        }

        SavedPaymentAccount row = SavedPaymentAccount.builder()
                .id(idForSave)
                .username(username)
                .operatorName(payload.getOperatorName())
                .operatorType(payload.getOperatorType())
                .accountNumber(payload.getAccountNumber())
                .accountName(payload.getAccountName())
                .accountBank(payload.getAccountBank())
                .countryCode(payload.getCountryCode())
                .currencyCode(payload.getCurrencyCode())
                .networkId(payload.getNetworkId())
                .idType(payload.getIdType())
                .idNumber(payload.getIdNumber())
                .additionalIdType(payload.getAdditionalIdType())
                .additionalIdNumber(payload.getAdditionalIdNumber())
                .dateOfBirth(payload.getDateOfBirth())
                .address(payload.getAddress())
                .email(payload.getEmail())
                .defaultFlag(payload.isDefaultAccount())
                .createdAt(entity != null ? entity.getCreatedAt() : Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.save(row);
        return toPayload(row);
    }

    @Override
    @Transactional
    public void delete(String username, String id) {
        SavedPaymentAccount entity = repository.findById(id).orElse(null);
        if (entity == null || !username.equals(entity.getUsername())) {
            throw new IllegalArgumentException("Compte introuvable");
        }
        repository.delete(entity);
    }

    private SavedPaymentAccountPayload toPayload(SavedPaymentAccount e) {
        SavedPaymentAccountPayload p = new SavedPaymentAccountPayload();
        p.setId(e.getId());
        p.setOperatorName(e.getOperatorName());
        p.setOperatorType(e.getOperatorType());
        p.setAccountNumber(e.getAccountNumber());
        p.setAccountName(e.getAccountName());
        p.setAccountBank(e.getAccountBank());
        p.setCountryCode(e.getCountryCode());
        p.setCurrencyCode(e.getCurrencyCode());
        p.setNetworkId(e.getNetworkId());
        p.setIdType(e.getIdType());
        p.setIdNumber(e.getIdNumber());
        p.setAdditionalIdType(e.getAdditionalIdType());
        p.setAdditionalIdNumber(e.getAdditionalIdNumber());
        p.setDateOfBirth(e.getDateOfBirth());
        p.setAddress(e.getAddress());
        p.setEmail(e.getEmail());
        p.setDefaultAccount(e.isDefaultFlag());
        return p;
    }
}
