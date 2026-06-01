package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.RestCountryResponse;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.service.CountrySynchronizationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.RestCountriesClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service de synchronisation des pays depuis l'API REST Countries
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CountrySynchronizationServiceImpl implements CountrySynchronizationService {
    
    private final RestCountriesClientService restCountriesClientService;
    private final CountryCurrencyRepository countryCurrencyRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    
    @Override
    public ResponseEntity<CountrySyncResult> synchronizeAllCountries() {
        log.info("🔄 Début de la synchronisation de tous les pays depuis REST Countries API");
        
        // Récupérer tous les pays depuis l'API
        ResponseEntity<List<RestCountryResponse>> apiResponse = restCountriesClientService.getAllCountries();
        
        if (!apiResponse.getStatusCode().is2xxSuccessful() || apiResponse.getBody() == null) {
            log.error("❌ Impossible de récupérer les pays depuis l'API REST Countries");
            return ResponseEntity.status(apiResponse.getStatusCode())
                    .body(new CountrySyncResult(0, 0, 0, 1, 
                            List.of("Erreur lors de la récupération des pays depuis l'API")));
        }
        
        List<RestCountryResponse> countries = apiResponse.getBody();
        if (countries == null || countries.isEmpty()) {
            log.warn("⚠️ Aucun pays récupéré depuis l'API");
            return ResponseEntity.ok(new CountrySyncResult(0, 0, 0, 0, List.of()));
        }
        
        log.info("📥 {} pays récupérés depuis l'API", countries.size());
        
        int created = 0;
        int updated = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();
        
        // Créer un TransactionTemplate pour gérer manuellement les transactions
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        def.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager, def);
        
        // Synchroniser chaque pays dans sa propre transaction isolée
        for (RestCountryResponse apiCountry : countries) {
            try {
                Boolean wasNew = transactionTemplate.execute(status -> {
                    try {
                        return synchronizeCountry(apiCountry);
                    } catch (Exception e) {
                        // Marquer la transaction pour rollback uniquement pour cette transaction
                        status.setRollbackOnly();
                        throw e;
                    }
                });
                
                if (wasNew != null) {
                    if (wasNew) {
                        created++;
                    } else {
                        // Pays existant, mis à jour avec les nouvelles données de l'API
                        updated++;
                    }
                }
                
            } catch (Exception e) {
                String countryCode = apiCountry.getCountryCode() != null ? 
                        apiCountry.getCountryCode() : "UNKNOWN";
                
                // Si l'erreur est due à une duplication de clé primaire, c'est probablement que le pays existe déjà
                // On ignore simplement l'erreur et on continue
                if (e.getMessage() != null && e.getMessage().contains("duplicate key value violates unique constraint")) {
                    log.warn("⚠️ Conflit de clé primaire pour {} - probablement que le pays existe déjà, on ignore", countryCode);
                    updated++; // Compter comme "mis à jour" (existant)
                    continue; // Passer au pays suivant
                }
                
                log.error("❌ Erreur lors de la synchronisation du pays {}: {}", 
                        countryCode, e.getMessage());
                errors++;
                errorMessages.add(String.format("Erreur pour %s: %s", 
                        countryCode, e.getMessage()));
            }
        }
        
        CountrySyncResult result = new CountrySyncResult(
                countries.size(),
                created,
                updated,
                errors,
                errorMessages
        );
        
        log.info("✅ Synchronisation terminée: {} pays traités, {} créés, {} mis à jour, {} erreurs",
                countries.size(), created, updated, errors);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Synchronise un pays individuel
     * Cette méthode est appelée depuis une transaction gérée manuellement via TransactionTemplate
     * @return true si le pays était nouveau, false s'il existait déjà (et a été mis à jour)
     */
    private boolean synchronizeCountry(RestCountryResponse apiCountry) {
        if (apiCountry.getCountryCode() == null || apiCountry.getCountryCode().isEmpty()) {
            log.warn("⚠️ Pays ignoré: code pays manquant");
            throw new IllegalArgumentException("Code pays manquant");
        }
        
        String countryCode = apiCountry.getCountryCode().toUpperCase();
        
        // Vérifier si le pays existe déjà par code pays
        Optional<CountryCurrency> existingCountryOpt = Optional.ofNullable(
                countryCurrencyRepository.findCountryCurrencyByCountryCode(countryCode)
        );
        
        CountryCurrency countryCurrency;
        boolean isNew;
        
        if (existingCountryOpt.isPresent()) {
            // Le pays existe déjà, on le met à jour avec les nouvelles données de l'API
            countryCurrency = existingCountryOpt.get();
            // L'ID est préservé car on utilise l'entité existante
            log.debug("🔄 Pays {} existe déjà (ID: {}), flag_url actuel: {}, mise à jour des données", 
                    countryCode, countryCurrency.getId(), countryCurrency.getFlagUrl());
            isNew = false;
        } else {
            // Le pays n'existe pas, on va le créer
            countryCurrency = new CountryCurrency();
            countryCurrency.setActivated(true); // Activer par défaut les nouveaux pays
            log.debug("➕ Création du nouveau pays: {}", countryCode);
            isNew = true;
        }
        
        // Mettre à jour les informations depuis l'API (pour nouveau ou existant)
        String oldFlagUrl = countryCurrency.getFlagUrl();
        updateCountryFromApiResponse(countryCurrency, apiCountry);
        String newFlagUrl = countryCurrency.getFlagUrl();
        
        // Sauvegarder - utiliser merge() pour forcer la mise à jour même si Hibernate ne détecte pas de changement
        if (isNew) {
            countryCurrencyRepository.save(countryCurrency);
        } else {
            // Pour les pays existants, utiliser merge() pour forcer la mise à jour
            countryCurrency = entityManager.merge(countryCurrency);
        }
        entityManager.flush();
        
        // Log pour vérifier si le drapeau a changé
        if (isNew || (oldFlagUrl == null && newFlagUrl != null) || 
            (oldFlagUrl != null && !oldFlagUrl.equals(newFlagUrl))) {
            log.info("🏳️ Flag URL mis à jour pour {}: {} -> {}", 
                    countryCode, oldFlagUrl, newFlagUrl);
        }
        
        if (isNew) {
            log.debug("✅ Nouveau pays créé: {} - ID: {}", countryCode, countryCurrency.getId());
        } else {
            log.debug("✅ Pays mis à jour: {} - ID: {}", countryCode, countryCurrency.getId());
        }
        
        return isNew; // true si nouveau, false si mis à jour
    }
    
    /**
     * Met à jour un CountryCurrency avec les données de l'API REST Countries
     */
    private void updateCountryFromApiResponse(CountryCurrency countryCurrency, RestCountryResponse apiCountry) {
        // Code pays (ISO 2 lettres)
        if (apiCountry.getCountryCode() != null) {
            countryCurrency.setCountryCode(apiCountry.getCountryCode().toUpperCase());
        }
        
        // Nom du pays (priorité: français common > anglais common > français official > anglais official)
        String countryName = apiCountry.getCountryNameInFrench();
        if (countryName != null && !countryName.isEmpty()) {
            countryCurrency.setCountryName(countryName);
        } else if (apiCountry.getName() != null && apiCountry.getName().getCommon() != null) {
            // Fallback sur le nom commun en anglais
            countryCurrency.setCountryName(apiCountry.getName().getCommon());
        } else if (apiCountry.getName() != null && apiCountry.getName().getOfficial() != null) {
            // Dernier fallback sur le nom officiel
            countryCurrency.setCountryName(apiCountry.getName().getOfficial());
        }
        
        // Code devise
        String currencyCode = apiCountry.getPrimaryCurrencyCode();
        if (currencyCode != null && !currencyCode.isEmpty()) {
            countryCurrency.setCurrencyCode(currencyCode);
        }
        
        // Indicatif téléphonique
        Integer callingCode = apiCountry.getCallingCode();
        if (callingCode != null) {
            countryCurrency.setCallingCode(callingCode);
        }
        
        // Capitale
        String capital = apiCountry.getCapitalCity();
        if (capital != null && !capital.isEmpty()) {
            countryCurrency.setCapital(capital);
        }
        
        // Continent
        String continent = apiCountry.getContinent();
        if (continent != null && !continent.isEmpty()) {
            // Normaliser le nom du continent (ex: "Africa" -> "Afrique")
            countryCurrency.setContinentName(normalizeContinentName(continent));
        }
        
        // Drapeau (URL directe du logo)
        String flagUrl = apiCountry.getFlagUrl();
        if (flagUrl != null && !flagUrl.isEmpty()) {
            countryCurrency.setFlagUrl(flagUrl);
            log.debug("🏳️ Drapeau récupéré pour {}: {}", 
                    apiCountry.getCountryCode(), flagUrl);
        } else {
            log.debug("⚠️ Aucun drapeau disponible pour {}", 
                    apiCountry.getCountryCode());
        }
    }
    
    /**
     * Normalise le nom du continent (traduction en français si nécessaire)
     */
    private String normalizeContinentName(String continent) {
        return switch (continent.toLowerCase()) {
            case "africa" -> "Afrique";
            case "europe" -> "Europe";
            case "asia" -> "Asie";
            case "north america" -> "Amérique du Nord";
            case "south america" -> "Amérique du Sud";
            case "oceania" -> "Océanie";
            case "antarctica" -> "Antarctique";
            default -> continent;
        };
    }
}

