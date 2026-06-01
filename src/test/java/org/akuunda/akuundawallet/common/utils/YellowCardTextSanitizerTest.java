package org.akuunda.akuundawallet.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class YellowCardTextSanitizerTest {

    @Test
    void sanitizePersonName_fixesApostrophePlaceholder() {
        assertEquals("N'Guessan Anne fleur",
                YellowCardTextSanitizer.sanitizePersonName("N?Guessan Anne fleur"));
    }

    @Test
    void sanitizePersonName_stripsInvalidCharacters() {
        assertEquals("Jean Dupont",
                YellowCardTextSanitizer.sanitizePersonName("Jean@Dupont#"));
    }

    @Test
    void sanitizePersonName_preservesAccents() {
        assertEquals("Amélie Durand",
                YellowCardTextSanitizer.sanitizePersonName("Amélie Durand"));
    }

    @Test
    void sanitizeFreeText_fixesDepositReason() {
        assertEquals("Depot de fonds",
                YellowCardTextSanitizer.sanitizeFreeText("D?p?t de fonds"));
    }

    @Test
    void sanitizePersonName_nullSafe() {
        assertEquals(null, YellowCardTextSanitizer.sanitizePersonName(null));
        assertEquals("", YellowCardTextSanitizer.sanitizePersonName("   "));
    }
}
