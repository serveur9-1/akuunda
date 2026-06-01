package org.akuunda.akuundawallet.wallet.api.enums;

/**
 * Constantes harmonisées pour les désignations d'opérations.
 * Chaque constante définit : designation, transactionType (label affiché), type (CREDIT/DEBIT), provider.
 */
public enum OperationDesignation {

    // ── Dépôts (ON_RAMP) ──
    MELD_ON_RAMP("Dépôt", "CREDIT", "Meld"),
    YELLOWCARD_ON_RAMP("Dépôt", "CREDIT", "YellowCard"),

    // ── Retraits (OFF_RAMP) ──
    GUARDARIAN_OFF_RAMP("Retrait", "DEBIT", "Guardarian"),
    YELLOWCARD_OFF_RAMP("Retrait", "DEBIT", "YellowCard"),
    MELD_OFF_RAMP("Retrait", "DEBIT", "Meld"),

    // ── Recevoir (liens de paiement) ──
    ONE_TIME_PAYMENT_RECEIVED("Encaissement", "CREDIT", "Externe"),

    // ── Paiement conditionnel externe (par lien) ──
    CONDITIONAL_PAYMENT_RECEIVED_EXTERNAL("Encaissement", "CREDIT", "Externe"),
    CONDITIONAL_PAYMENT_SENT_EXTERNAL("Paiement", "DEBIT", "Externe"),

    // ── QR Payment ──
    QR_PAYMENT_SENT("Paiement", "DEBIT", "Interne"),
    QR_PAYMENT_RECEIVED("Reçu", "CREDIT", "Interne"),

    // ── Paiement conditionnel interne ──
    CONDITIONAL_PAYMENT_SENT("Paiement", "DEBIT", "Interne"),
    CONDITIONAL_PAYMENT_RECEIVED("Encaissement", "CREDIT", "Interne"),

    // ── Paiement conditionnel legacy (designation brute "CONDITIONAL_PAYMENT") ──
    CONDITIONAL_PAYMENT("Encaissement", "CREDIT", "Interne"),
    CONDITIONAL_PAYMENT_REFUND("Encaissement", "CREDIT", "Interne"),
    CONDITIONAL_PAYMENT_CANCELLED("Encaissement", "CREDIT", "Interne"),

    // ── Transfert interne (entre comptes Akuunda Pay) ──
    INTERNAL_TRANSFER_SENT("Envoi", "DEBIT", "Interne"),
    INTERNAL_TRANSFER_RECEIVED("Reçu", "CREDIT", "Interne"),

    // ── Transfert externe (vers wallet on-chain hors Akuunda) ──
    // Étape technique d'un off-ramp / retrait : la valeur user est déjà
    // représentée par l'opération "Retrait" du provider (YellowCard, Meld,
    // Guardarian, ...), on masque donc ces lignes dans les listings users
    // pour éviter la duplication. Conservées en base pour l'audit admin.
    EXTERNAL_TRANSFER_SENT("Transfert externe", "DEBIT", "Système");

    private final String transactionType;
    private final String type;
    private final String provider;

    OperationDesignation(String transactionType, String type, String provider) {
        this.transactionType = transactionType;
        this.type = type;
        this.provider = provider;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getType() {
        return type;
    }

    public String getProvider() {
        return provider;
    }

    /**
     * Résout le transactionType à partir d'une designation string.
     * Compatible avec les anciennes et nouvelles designations.
     */
    public static String resolveTransactionType(String designation, String type) {
        if (designation == null || designation.isBlank()) {
            return resolveFromType(type);
        }

        String upper = designation.toUpperCase().trim();

        // ── Nouvelles designations (exactes) ──
        try {
            OperationDesignation resolved = OperationDesignation.valueOf(upper);
            if (resolved == CONDITIONAL_PAYMENT) {
                return "DEBIT".equalsIgnoreCase(type) ? "Paiement" : "Encaissement";
            }
            return resolved.getTransactionType();
        } catch (IllegalArgumentException ignored) {
        }

        // ── Legacy designations ──
        if (upper.contains("OFF_RAMP") || upper.contains("OFFRAMP") || upper.contains("OFF RAMP")) return "Retrait";
        if (upper.contains("ON_RAMP") || upper.contains("ONRAMP") || upper.contains("ON RAMP")) return "Dépôt";
        if (upper.contains("CONDITIONAL_PAYMENT_SENT")) return "Paiement";
        if (upper.contains("CONDITIONAL_PAYMENT_RECEIVED")) return "Encaissement";
        if (upper.contains("CONDITIONAL_PAYMENT_CANCELLED")) return "Encaissement";
        if (upper.contains("CONDITIONAL_PAYMENT_REFUND")) return "Encaissement";
        if (upper.contains("CONDITIONAL_PAYMENT")) {
            return "DEBIT".equalsIgnoreCase(type) ? "Paiement" : "Encaissement";
        }
        if (upper.contains("QR_PAYMENT_SENT")) return "Paiement";
        if (upper.contains("QR_PAYMENT_RECEIVED")) return "Reçu";
        if (upper.contains("ONE_TIME_PAYMENT_RECEIVED")) return "Encaissement";
        if (upper.contains("EXTERNAL_TRANSFER_SENT")) return "Transfert externe";
        if (upper.contains("INTERNAL_TRANSFER_SENT")) return "Envoi";
        if (upper.contains("INTERNAL_TRANSFER_RECEIVED")) return "Reçu";
        if (upper.contains("TRANSFERT")) {
            return "DEBIT".equalsIgnoreCase(type) ? "Envoi" : "Reçu";
        }
        if (upper.contains("SWAP")) return "Swap";

        return resolveFromType(type);
    }

    /**
     * Résout le provider à partir d'une designation string.
     * ✅ CORRIGÉ : Supporte "Yellow Card OnRamp" (avec espace) en plus de YELLOWCARD et YELLOW_CARD.
     */
    public static String resolveProvider(String designation) {
        if (designation == null || designation.isBlank()) return "Inconnu";

        String upper = designation.toUpperCase().trim();

        try {
            OperationDesignation resolved = OperationDesignation.valueOf(upper);
            return resolved.getProvider();
        } catch (IllegalArgumentException ignored) {
        }

        // Legacy — ✅ CORRIGÉ : ajout "YELLOW CARD" (avec espace)
        if (upper.contains("MELD")) return "Meld";
        if (upper.contains("YELLOWCARD") || upper.contains("YELLOW_CARD") || upper.contains("YELLOW CARD")) return "YellowCard";
        if (upper.contains("GUADARIAN") || upper.contains("GUARDARIAN")) return "Guardarian";
        if (upper.contains("MT_PELERIN") || upper.contains("MTPELERIN") || upper.contains("MT PELERIN")) return "MtPelerin";
        if (upper.contains("EXTERNAL_TRANSFER")) return "Système";
        if (upper.contains("TRANSFERT") || upper.contains("INTERNAL_TRANSFER")
                || upper.contains("QR_PAYMENT") || upper.contains("CONDITIONAL_PAYMENT_SENT")
                || upper.contains("CONDITIONAL_PAYMENT_RECEIVED")
                || upper.contains("CONDITIONAL_PAYMENT")) return "Interne";

        return "Inconnu";
    }

    /**
     * Returns {@code true} for operations that represent a technical step rather than a
     * user-meaningful movement, and should therefore be hidden from end-user / merchant listings.
     *
     * <p>This covers two cases:</p>
     * <ul>
     *   <li>{@code EXTERNAL_TRANSFER_SENT} — explicit tag for off-ramp / retrait token send.</li>
     *   <li>Legacy fallback: {@code INTERNAL_TRANSFER_SENT} with no counterpart username, which is
     *       how off-ramp transfers are stored before the explicit {@code EXTERNAL_TRANSFER_SENT}
     *       designation was introduced.</li>
     * </ul>
     * <p>Operations are still persisted and visible to the admin backoffice for audit.</p>
     */
    /** Seuil max (POL/MATIC) des crédits gas automatiques legacy (~0,07 à l'activation). */
    private static final double INTERNAL_GAS_GRANT_MAX_AMOUNT = 0.15;

    public static boolean isHiddenSystemTransfer(String designation, String counterpartUsername) {
        return isHiddenSystemTransfer(designation, counterpartUsername, null);
    }

    /**
     * Opérations techniques masquées dans le panel Pro / wallet user (audit admin conservé).
     */
    public static boolean isHiddenSystemTransfer(String designation, String counterpartUsername, Double amount) {
        if (designation == null) return false;
        String upper = designation.toUpperCase().trim();
        if (upper.equals(EXTERNAL_TRANSFER_SENT.name())) return true;
        if (upper.equals(INTERNAL_TRANSFER_SENT.name())
                && (counterpartUsername == null || counterpartUsername.isBlank())) {
            return true;
        }
        if (isInternalGasGrant(upper, amount)) return true;
        return false;
    }

    /**
     * Crédit MATIC/POL système (ex. 0,07 à la création de compte) — pas une opération métier client.
     */
    private static boolean isInternalGasGrant(String designationUpper, Double amount) {
        if (amount == null || amount <= 0) return false;
        if (!designationUpper.equals("TRANSFERT") && !designationUpper.contains("GAS")) {
            return false;
        }
        return amount <= INTERNAL_GAS_GRANT_MAX_AMOUNT;
    }

    /**
     * Détermine si l'opération est un crédit (entrée) ou un débit (sortie) en
     * se basant en priorité sur la <b>designation</b> — qui est la source de
     * vérité métier — et seulement en repli sur la colonne {@code type}.
     *
     * <p>Cette indirection est nécessaire car certaines intégrations historiques
     * (Guardarian, MT Pelerin) ont longtemps écrit {@code type=CREDIT} pour les
     * off-ramps et {@code type=DEBIT} pour les on-ramps. Le code des contrôleurs
     * (KPI marchand, historique, etc.) doit ignorer cette donnée incohérente.</p>
     *
     * @return {@code true} si l'opération est un crédit (entrée),
     *         {@code false} si c'est un débit, sinon la valeur de fallback.
     */
    public static boolean isCredit(String designation, String type, boolean defaultIfUnknown) {
        String d = designation == null ? "" : designation.toUpperCase().trim();
        // Off-ramp / retrait / transfert sortant → DEBIT, sauf REFUND/CANCELLED
        // qui ramènent les fonds sur le wallet.
        if (d.contains("OFF_RAMP") || d.contains("OFFRAMP") || d.contains("OFF RAMP")
                || d.contains("RETRAIT") || d.contains("WITHDRAW")
                || d.contains("TRANSFER_SENT") || d.contains("INTERNAL_TRANSFER_SENT")
                || d.contains("EXTERNAL_TRANSFER_SENT")
                || d.contains("QR_PAYMENT_SENT") || d.contains("CONDITIONAL_PAYMENT_SENT")) {
            if (d.contains("REFUND") || d.contains("CANCELLED")) return true;
            return false;
        }
        // On-ramp / rechargement / paiement reçu → CREDIT
        if (d.contains("ON_RAMP") || d.contains("ONRAMP") || d.contains("ON RAMP")
                || d.contains("RECHARGEMENT") || d.contains("DEPOSIT") || d.contains("DEPOT") || d.contains("DÉPÔT")
                || d.contains("TRANSFER_RECEIVED") || d.contains("INTERNAL_TRANSFER_RECEIVED")
                || d.contains("PAYMENT_RECEIVED") || d.contains("QR_PAYMENT_RECEIVED")
                || d.contains("ONE_TIME_PAYMENT_RECEIVED")
                || d.contains("CONDITIONAL_PAYMENT_RECEIVED") || d.contains("CONDITIONAL_PAYMENT_REFUND")) {
            return true;
        }
        // Repli sur la colonne type.
        if (type != null) {
            String t = type.toUpperCase().trim();
            if ("CREDIT".equals(t)) return true;
            if ("DEBIT".equals(t)) {
                return d.contains("REFUND") || d.contains("CANCELLED");
            }
        }
        return defaultIfUnknown;
    }

    private static String resolveFromType(String type) {
        if (type == null) return "Inconnu";
        return switch (type.toUpperCase()) {
            case "CREDIT" -> "Dépôt";
            case "DEBIT" -> "Retrait";
            default -> "Inconnu";
        };
    }
}
