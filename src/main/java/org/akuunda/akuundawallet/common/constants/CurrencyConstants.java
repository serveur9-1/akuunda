package org.akuunda.akuundawallet.common.constants;

import lombok.experimental.UtilityClass;
import org.akuunda.akuundawallet.wallet.api.entities.Currency;
import java.util.List;

@UtilityClass
public class CurrencyConstants {

    public static final List<Currency> CURRENCIES;

    static {

        CURRENCIES = List.of(
                    new Currency("🇮🇳", "Indian Rupee", "INR", true, true, 1),
                    new Currency("🇹🇷", "Turkish lira", "TRY", true, true, 2),
                    new Currency("🇦🇪", "Arab Emirates Dirham", "AED", true, false, 3),
                    new Currency("🇲🇽", "Mexican Peso", "MXN", true, true, 4),
                    new Currency("🇻🇳", "Vietnamese dong", "VND", true, true, 5),
                    new Currency("🇳🇬", "Nigerian Naira", "NGN", true, true, 6),
                    new Currency("🇧🇷", "Brazilian Real", "BRL", true, true, 7),
                    new Currency("🇵🇪", "Peruvian sol", "PEN", true, true, 8),
                    new Currency("🇨🇴", "Colombian Peso", "COP", true, true, 9),
                    new Currency("🇨🇱", "Chilean Peso", "CLP", true, true, 10),
                    new Currency("🇵🇭", "Philippine Peso", "PHP", true, true, 11),
                    new Currency("🇪🇺", "Euro", "EUR", true, true, 12),
                    new Currency("🇮🇩", "Indonesian Rupiah", "IDR", true, true, 14),
                    new Currency("🇰🇪", "Kenya Shillings", "KES", true, true, 15),
                    new Currency("🇬🇭", "Ghanian Cedi", "GHS", true, true, 16),
                    new Currency("🇿🇦", "South African Rand", "RAND", true, false, 17),
                    new Currency("🇷🇼", "Rwandan Franc", "RWF", true, true, 18),
                    new Currency("🇨🇲", "Central African CFA franc", "XAF", true, true, 19),
                    new Currency("🇬🇧", "Great Britain Pounds", "GBP", true, true, 20),
                    new Currency("🇺🇸", "United States Dollar", "USD", true, false, 21),
                    new Currency("🇧🇼", "Botswana Pula", "BWP", true, true, 22),
                    new Currency("🇲🇼", "Malawian Kwacha", "MWK", true, true, 23),
                    new Currency("🇹🇿", "Tanzanian Shilling", "TZS", true, true, 24),
                    new Currency("🇺🇬", "Ugandan Shilling", "UGX", true, true, 25),
                    new Currency("🇿🇲", "Zambian Kwacha", "ZMW", true, true, 26),
                    new Currency("🇹🇭", "Thai Baht", "THB", false, false, 27),
                    new Currency("🇲🇾", "Malaysian Ringgit", "MYR", false, false, 28),
                    new Currency("🇦🇷", "Argentine Peso", "ARS", true, true, 29),
                    new Currency("🇦🇺", "Australian Dollar", "AUD", false, false, 30),
                    new Currency("🇪🇬", "Egyptian Pound", "EGP", false, false, 31),
                    new Currency("🇱🇰", "Sri Lankan Rupee", "LKR", false, false, 32),
                    new Currency("🇵🇱", "Polish Zloty", "PLN", true, false, 33),
                    new Currency("🇹🇼", "New Taiwan Dollar", "TWN", false, false, 34),
                    new Currency("🇸🇬", "Singapore Dollar", "SGD", false, false, 35),
                    new Currency("🇳🇿", "New Zealand Dollar", "NZD", false, false, 36),
                    new Currency("🇯🇵", "Japanese Yen", "JPY", false, false, 37),
                    new Currency("🇰🇷", "South Korean Won", "KRW", false, false, 38),
                    new Currency("🇧🇯", "West African CFA Franc (Benin)", "XOF", true, false, 39),
                    new Currency("🇨🇩", "Congolese Franc", "CDF", true, false, 40),
                    new Currency("🇬🇦", "Central African CFA Franc (Gabon)", "GAB", true, false, 41),
                    new Currency("🇨🇬", "Central African CFA franc (Republic of Congo)", "COG", true, false, 42),
                    new Currency("🇪🇨", "United States Dollar (Ecuador)", "USD", false, true, 43),
                    new Currency("🇵🇾", "Paraguayan Guarani", "PYG", false, true, 44),
                    new Currency("🇺🇾", "Uruguayan Peso", "UYU", false, true, 45),
                    new Currency("🇵🇦", "United States Dollar (Panama)", "USD", false, true, 46),
                    new Currency("🇨🇷", "Costa Rican Colón", "CRC", false, true, 47),
                    new Currency("🇬🇹", "Guatemalan Quetzal", "GTQ", false, true, 48),
                    new Currency("🇸🇻", "United States Dollar (El Salvador)", "USD", false, true, 49),
                    new Currency("🇨🇦", "Canadian Dollar", "CAD", true, true, 50),
                    new Currency("🇧🇴", "Bolivian Boliviano", "BOB", false, true, 51),
                    new Currency("🇻🇪", "Venezuelan Bolívar Soberano", "VES", false, true, 52)
                );
    }
}
