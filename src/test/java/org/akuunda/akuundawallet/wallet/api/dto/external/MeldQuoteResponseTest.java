package org.akuunda.akuundawallet.wallet.api.dto.external;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour la logique de recommandation et de tri des providers dans MeldQuoteResponse.
 */
public class MeldQuoteResponseTest {

    /**
     * Test : Petits montants (10-14000 EUR) - Ordre de priorité UNLIMIT → MERCURYO → BANXA
     */
    @Test
    void testSmallMediumAmountRecommendations() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Créer des quotes avec différents providers pour 15 EUR en France
        quotes.add(createQuote("KRYPTONIM", 15.0, "FR"));
        quotes.add(createQuote("UNLIMIT", 15.0, "FR"));
        quotes.add(createQuote("REVOLUT", 15.0, "FR"));
        quotes.add(createQuote("BANXA", 15.0, "FR"));
        quotes.add(createQuote("MERCURYO", 15.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Vérifier l'ordre attendu : UNLIMIT, MERCURYO, BANXA (recommandés), puis KRYPTONIM, REVOLUT (non recommandés)
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals(5, sorted.size());

        // Les 3 premiers doivent être recommandés
        assertEquals("UNLIMIT", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());

        assertEquals("MERCURYO", sorted.get(1).getServiceProvider());
        assertTrue(sorted.get(1).getRecommended());

        assertEquals("BANXA", sorted.get(2).getServiceProvider());
        assertTrue(sorted.get(2).getRecommended());

        // Les 2 derniers ne doivent pas être recommandés
        assertFalse(sorted.get(3).getRecommended());
        assertFalse(sorted.get(4).getRecommended());
    }

    /**
     * Test : Gros montants (14000-80000 EUR) - Seul TRANSFI est recommandé
     */
    @Test
    void testLargeAmountRecommendations() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Créer des quotes pour 15000 EUR
        quotes.add(createQuote("UNLIMIT", 15000.0, "FR"));
        quotes.add(createQuote("TRANSFI", 15000.0, "FR"));
        quotes.add(createQuote("BANXA", 15000.0, "FR"));
        quotes.add(createQuote("MERCURYO", 15000.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Vérifier que seul TRANSFI est recommandé
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("TRANSFI", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());

        // Les autres ne doivent pas être recommandés
        assertFalse(sorted.get(1).getRecommended());
        assertFalse(sorted.get(2).getRecommended());
        assertFalse(sorted.get(3).getRecommended());
    }

    /**
     * Test : Pays africain - FONBNK est recommandé
     */
    @Test
    void testAfricanCountryRecommendations() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Créer des quotes pour le Nigeria (NG)
        quotes.add(createQuote("UNLIMIT", 100.0, "NG"));
        quotes.add(createQuote("FONBNK", 100.0, "NG"));
        quotes.add(createQuote("BANXA", 100.0, "NG"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Vérifier que seul FONBNK est recommandé
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("FONBNK", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());

        // Les autres ne doivent pas être recommandés
        assertFalse(sorted.get(1).getRecommended());
        assertFalse(sorted.get(2).getRecommended());
    }

    /**
     * Test : Plusieurs pays africains doivent être reconnus
     */
    @Test
    void testMultipleAfricanCountries() {
        String[] africanCountries = {"DZ", "NG", "ZA", "EG", "KE", "MA", "TZ", "GH"};

        for (String country : africanCountries) {
            MeldQuoteResponse response = new MeldQuoteResponse();
            List<MeldQuote> quotes = new ArrayList<>();

            quotes.add(createQuote("UNLIMIT", 100.0, country));
            quotes.add(createQuote("FONBNK", 100.0, country));

            response.setQuotes(quotes);
            response.applyRecommendations();

            MeldQuote firstQuote = response.getQuotes().get(0);
            assertEquals("FONBNK", firstQuote.getServiceProvider(),
                    "FONBNK devrait être recommandé pour " + country);
            assertTrue(firstQuote.getRecommended(),
                    "FONBNK devrait être marqué comme recommandé pour " + country);
        }
    }

    /**
     * Test : Montant au seuil de 14000 EUR (doit utiliser UNLIMIT/MERCURYO/BANXA)
     */
    @Test
    void testThresholdBoundary() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Exactement 14000 EUR
        quotes.add(createQuote("UNLIMIT", 14000.0, "FR"));
        quotes.add(createQuote("TRANSFI", 14000.0, "FR"));
        quotes.add(createQuote("MERCURYO", 14000.0, "FR"));
        quotes.add(createQuote("BANXA", 14000.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // UNLIMIT, MERCURYO, BANXA devraient être recommandés à 14000 (inclus dans la plage petits/moyens montants)
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("UNLIMIT", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());
        
        assertEquals("MERCURYO", sorted.get(1).getServiceProvider());
        assertTrue(sorted.get(1).getRecommended());
        
        assertEquals("BANXA", sorted.get(2).getServiceProvider());
        assertTrue(sorted.get(2).getRecommended());
        
        // TRANSFI ne devrait pas être recommandé à 14000
        assertEquals("TRANSFI", sorted.get(3).getServiceProvider());
        assertFalse(sorted.get(3).getRecommended());
    }

    /**
     * Test : Montant juste en dessous du seuil (13999 EUR) doit utiliser UNLIMIT/MERCURYO/BANXA
     */
    @Test
    void testJustBelowThreshold() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        quotes.add(createQuote("TRANSFI", 13999.0, "FR"));
        quotes.add(createQuote("UNLIMIT", 13999.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // UNLIMIT devrait être recommandé en dessous de 14000
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("UNLIMIT", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());
        assertFalse(sorted.get(1).getRecommended());
    }

    /**
     * Test : Montant juste au-dessus du seuil (14001 EUR) doit utiliser TRANSFI
     */
    @Test
    void testJustAboveThreshold() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Exactement 14001 EUR
        quotes.add(createQuote("UNLIMIT", 14001.0, "FR"));
        quotes.add(createQuote("TRANSFI", 14001.0, "FR"));
        quotes.add(createQuote("MERCURYO", 14001.0, "FR"));
        quotes.add(createQuote("BANXA", 14001.0, "FR"));
        quotes.add(createQuote("REVOLUT", 14001.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // TRANSFI devrait être recommandé au-dessus de 14000
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("TRANSFI", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());
        
        // Les autres ne devraient pas être recommandés
        assertFalse(sorted.get(1).getRecommended());
        assertFalse(sorted.get(2).getRecommended());
        assertFalse(sorted.get(3).getRecommended());
        assertFalse(sorted.get(4).getRecommended());
    }

    /**
     * Test : Montant au-dessus du seuil maximal (> 80000 EUR) - aucun recommandé
     */
    @Test
    void testAboveMaxThreshold() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        quotes.add(createQuote("TRANSFI", 85000.0, "FR"));
        quotes.add(createQuote("UNLIMIT", 85000.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Aucun provider ne devrait être recommandé au-dessus de 80000
        for (MeldQuote quote : response.getQuotes()) {
            assertFalse(quote.getRecommended());
        }
    }

    /**
     * Test : Liste vide de quotes
     */
    @Test
    void testEmptyQuotesList() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        response.setQuotes(new ArrayList<>());

        // Ne devrait pas lancer d'exception
        assertDoesNotThrow(() -> response.applyRecommendations());
        assertEquals(0, response.getQuotes().size());
    }

    /**
     * Test : Liste null de quotes
     */
    @Test
    void testNullQuotesList() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        response.setQuotes(null);

        // Ne devrait pas lancer d'exception
        assertDoesNotThrow(() -> response.applyRecommendations());
    }

    /**
     * Test : Quote avec provider null
     */
    @Test
    void testQuoteWithNullProvider() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        MeldQuote nullProviderQuote = new MeldQuote();
        nullProviderQuote.setServiceProvider(null);
        nullProviderQuote.setSourceAmount(100.0);
        nullProviderQuote.setCountryCode("FR");

        quotes.add(nullProviderQuote);
        quotes.add(createQuote("UNLIMIT", 100.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Le quote avec provider null ne devrait pas être recommandé
        assertFalse(response.getQuotes().get(1).getRecommended());
        assertTrue(response.getQuotes().get(0).getRecommended());
    }

    /**
     * Test : Respect de l'ordre de priorité exact pour petits montants
     */
    @Test
    void testExactPriorityOrder() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        // Ajouter dans un ordre aléatoire
        quotes.add(createQuote("GUARDARIAN", 100.0, "FR"));
        quotes.add(createQuote("BANXA", 100.0, "FR"));
        quotes.add(createQuote("MERCURYO", 100.0, "FR"));
        quotes.add(createQuote("UNLIMIT", 100.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // Vérifier l'ordre exact : UNLIMIT (index 0), MERCURYO (index 1), BANXA (index 2), GUARDARIAN (index 3)
        List<MeldQuote> sorted = response.getQuotes();
        assertEquals("UNLIMIT", sorted.get(0).getServiceProvider());
        assertEquals("MERCURYO", sorted.get(1).getServiceProvider());
        assertEquals("BANXA", sorted.get(2).getServiceProvider());
        assertEquals("GUARDARIAN", sorted.get(3).getServiceProvider());
    }

    /**
     * Test : GUARDARIAN est recommandé pour les petits montants et apparaît après BANXA
     */
    @Test
    void testGuardarianRecommendedForSmallMediumAmounts() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        quotes.add(createQuote("GUARDARIAN", 500.0, "FR"));
        quotes.add(createQuote("KRYPTONIM", 500.0, "FR"));
        quotes.add(createQuote("UNLIMIT", 500.0, "FR"));
        quotes.add(createQuote("BANXA", 500.0, "FR"));
        quotes.add(createQuote("MERCURYO", 500.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        List<MeldQuote> sorted = response.getQuotes();

        // Les 4 premiers doivent être recommandés dans l'ordre de priorité
        assertEquals("UNLIMIT", sorted.get(0).getServiceProvider());
        assertTrue(sorted.get(0).getRecommended());

        assertEquals("MERCURYO", sorted.get(1).getServiceProvider());
        assertTrue(sorted.get(1).getRecommended());

        assertEquals("BANXA", sorted.get(2).getServiceProvider());
        assertTrue(sorted.get(2).getRecommended());

        assertEquals("GUARDARIAN", sorted.get(3).getServiceProvider());
        assertTrue(sorted.get(3).getRecommended());

        // KRYPTONIM ne doit pas être recommandé
        assertEquals("KRYPTONIM", sorted.get(4).getServiceProvider());
        assertFalse(sorted.get(4).getRecommended());
    }

    /**
     * Test : Montant minimum (10 EUR) devrait utiliser les petits montants
     */
    @Test
    void testMinimumAmount() {
        MeldQuoteResponse response = new MeldQuoteResponse();
        List<MeldQuote> quotes = new ArrayList<>();

        quotes.add(createQuote("UNLIMIT", 10.0, "FR"));
        quotes.add(createQuote("TRANSFI", 10.0, "FR"));

        response.setQuotes(quotes);
        response.applyRecommendations();

        // UNLIMIT devrait être recommandé pour 10 EUR
        assertEquals("UNLIMIT", response.getQuotes().get(0).getServiceProvider());
        assertTrue(response.getQuotes().get(0).getRecommended());
        assertFalse(response.getQuotes().get(1).getRecommended());
    }

    /**
     * Méthode utilitaire pour créer un MeldQuote
     */
    private MeldQuote createQuote(String provider, Double amount, String countryCode) {
        MeldQuote quote = new MeldQuote();
        quote.setServiceProvider(provider);
        quote.setSourceAmount(amount);
        quote.setCountryCode(countryCode);
        quote.setSourceCurrencyCode("EUR");
        quote.setDestinationCurrencyCode("USDC");
        return quote;
    }
}
