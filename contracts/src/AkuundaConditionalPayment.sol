// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/Pausable.sol";

/**
 * @title AkuundaConditionalPayment
 * @notice Smart contract générique et configurable pour paiements conditionnels
 *         avec redistribution automatique des commissions vers N bénéficiaires.
 *
 * @dev Déployé une seule fois sur Polygon. Chaque partenaire configure ses règles
 *      via registerConfig() sans nécessiter un nouveau déploiement.
 *
 *      Flow :
 *        1. registerConfig()   → Admin enregistre les bénéficiaires + parts du partenaire
 *        2. approve() + deposit() → Client verrouille les USDC dans ce contrat
 *        3. distribute()       → Service wallet libère vers tous les bénéficiaires (1 tx atomique)
 *           refund()           → Remboursement intégral si annulation dans la fenêtre gratuite
 *           distributeAndRefund() → Pénalité distribuée + reste remboursé au client
 *
 *      Adresse USDC Polygon : 0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359
 */
contract AkuundaConditionalPayment is AccessControl, ReentrancyGuard, Pausable {
    using SafeERC20 for IERC20;

    // -------------------------------------------------------------------------
    // Roles
    // -------------------------------------------------------------------------

    /// @notice Wallet service Akuunda — déclenche deposit/distribute/refund automatiquement
    bytes32 public constant RELAYER_ROLE = keccak256("RELAYER_ROLE");

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    uint256 public constant BPS_DENOMINATOR = 10_000;
    IERC20 public immutable usdc;

    // -------------------------------------------------------------------------
    // Structures
    // -------------------------------------------------------------------------

    struct Beneficiary {
        address wallet;
        uint16  sharesBps; // Sur 10 000. Ex: 8000 = 80%
    }

    /**
     * @notice Configuration de redistribution d'un partenaire.
     *         Immuable une fois enregistrée (transparence et confiance partenaire).
     */
    struct ContractConfig {
        Beneficiary[] beneficiaries;
        bool          active;
    }

    enum PaymentStatus {
        Pending,       // Fonds verrouillés, conditions non encore remplies
        Distributed,   // Redistribué aux bénéficiaires
        Refunded,      // Remboursement intégral au client
        PartialRefund  // Pénalité distribuée + reste remboursé
    }

    struct Payment {
        bytes32       configId;   // Référence à la config partenaire
        address       depositor;  // Wallet client (destinataire du remboursement éventuel)
        uint256       amount;     // Montant net USDC verrouillé (frais on-ramp déjà déduits)
        PaymentStatus status;
    }

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /// @dev configId = keccak256(contractCode) généré côté Java
    mapping(bytes32 => ContractConfig) private _configs;

    /// @dev paymentId = keccak256(paymentCode) généré côté Java
    mapping(bytes32 => Payment) private _payments;

    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------

    event ConfigRegistered(
        bytes32 indexed configId,
        address[]       beneficiaries,
        uint16[]        sharesBps
    );

    event ConfigDeactivated(bytes32 indexed configId);

    event PaymentDeposited(
        bytes32 indexed paymentId,
        bytes32 indexed configId,
        address indexed depositor,
        uint256         amount
    );

    event PaymentDistributed(
        bytes32   indexed paymentId,
        address[] beneficiaries,
        uint256[] amounts
    );

    event PaymentRefunded(
        bytes32 indexed paymentId,
        address indexed depositor,
        uint256         amount
    );

    event PaymentPartialRefund(
        bytes32 indexed paymentId,
        address indexed depositor,
        uint256         refundAmount,
        uint256         penaltyAmount
    );

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    constructor(address usdcAddress, address admin) {
        require(usdcAddress != address(0), "USDC address required");
        require(admin      != address(0), "Admin address required");
        usdc = IERC20(usdcAddress);
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
    }

    // -------------------------------------------------------------------------
    // Config management — DEFAULT_ADMIN_ROLE
    // -------------------------------------------------------------------------

    /**
     * @notice Enregistre la config de redistribution d'un partenaire.
     * @param configId          keccak256 du code contrat partenaire (généré Java)
     * @param beneficiaryAddrs  Adresses des bénéficiaires
     * @param sharesBps         Parts en basis points — doit totaliser exactement 10 000
     */
    function registerConfig(
        bytes32          configId,
        address[] calldata beneficiaryAddrs,
        uint16[]  calldata sharesBps
    ) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(!_configs[configId].active,                 "Config already active");
        require(beneficiaryAddrs.length > 0,                "No beneficiaries");
        require(beneficiaryAddrs.length == sharesBps.length,"Length mismatch");

        uint256 total;
        for (uint256 i; i < sharesBps.length; i++) {
            require(beneficiaryAddrs[i] != address(0), "Zero address beneficiary");
            require(sharesBps[i] > 0,                  "Share must be > 0");
            total += sharesBps[i];
        }
        require(total == BPS_DENOMINATOR, "Shares must sum to 10000 BPS");

        ContractConfig storage cfg = _configs[configId];
        cfg.active = true;
        for (uint256 i; i < beneficiaryAddrs.length; i++) {
            cfg.beneficiaries.push(Beneficiary({
                wallet:    beneficiaryAddrs[i],
                sharesBps: sharesBps[i]
            }));
        }

        emit ConfigRegistered(configId, beneficiaryAddrs, sharesBps);
    }

    /**
     * @notice Désactive une config. Les paiements en cours (Pending) ne sont pas affectés.
     */
    function deactivateConfig(bytes32 configId) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(_configs[configId].active, "Config not active");
        _configs[configId].active = false;
        emit ConfigDeactivated(configId);
    }

    // -------------------------------------------------------------------------
    // Payment lifecycle — RELAYER_ROLE
    // -------------------------------------------------------------------------

    /**
     * @notice Verrouille les USDC du client dans ce contrat.
     *
     *         Prérequis côté client (via Venly) :
     *           1. usdc.approve(address(this), amount)
     *           2. deposit(paymentId, configId, amount)   ← msg.sender = client wallet
     *
     *         Note : Les frais on-ramp sont déjà déduits du `amount` transmis.
     *                Ils sont payés par le partenaire et non récupérables.
     *
     * @param paymentId keccak256 du code paiement
     * @param configId  keccak256 du code contrat partenaire
     * @param amount    Montant net USDC (après frais on-ramp)
     */
    function deposit(
        bytes32 paymentId,
        bytes32 configId,
        uint256 amount
    ) external whenNotPaused nonReentrant {
        require(_payments[paymentId].depositor == address(0), "Payment already exists");
        require(_configs[configId].active,                    "Config not active");
        require(amount > 0,                                   "Amount must be > 0");

        _payments[paymentId] = Payment({
            configId:  configId,
            depositor: msg.sender,
            amount:    amount,
            status:    PaymentStatus.Pending
        });

        usdc.safeTransferFrom(msg.sender, address(this), amount);

        emit PaymentDeposited(paymentId, configId, msg.sender, amount);
    }

    /**
     * @notice Redistribue les USDC vers tous les bénéficiaires en une seule transaction atomique.
     *         Appelé automatiquement par le backend Akuunda quand les conditions du service sont remplies.
     *         Le dernier bénéficiaire absorbe l'éventuel dust de l'arrondi BPS.
     */
    function distribute(bytes32 paymentId)
        external
        onlyRole(RELAYER_ROLE)
        whenNotPaused
        nonReentrant
    {
        Payment storage pmt = _payments[paymentId];
        require(pmt.depositor != address(0),           "Payment not found");
        require(pmt.status == PaymentStatus.Pending,   "Payment not pending");

        ContractConfig storage cfg  = _configs[pmt.configId];
        uint256 total               = pmt.amount;
        uint256 remaining           = total;
        uint256 len                 = cfg.beneficiaries.length;

        pmt.status = PaymentStatus.Distributed;

        address[] memory addrs   = new address[](len);
        uint256[] memory amounts = new uint256[](len);

        for (uint256 i; i < len; i++) {
            Beneficiary memory b = cfg.beneficiaries[i];
            uint256 share = (i == len - 1)
                ? remaining
                : (total * b.sharesBps) / BPS_DENOMINATOR;

            remaining   -= share;
            addrs[i]     = b.wallet;
            amounts[i]   = share;

            usdc.safeTransfer(b.wallet, share);
        }

        emit PaymentDistributed(paymentId, addrs, amounts);
    }

    /**
     * @notice Rembourse l'intégralité des USDC au client.
     *         À utiliser dans la fenêtre d'annulation gratuite.
     *         Les frais on-ramp restent non récupérables (payés par le partenaire).
     */
    function refund(bytes32 paymentId)
        external
        onlyRole(RELAYER_ROLE)
        whenNotPaused
        nonReentrant
    {
        Payment storage pmt = _payments[paymentId];
        require(pmt.depositor != address(0),           "Payment not found");
        require(pmt.status == PaymentStatus.Pending,   "Payment not pending");

        address depositor = pmt.depositor;
        uint256 amount    = pmt.amount;
        pmt.status        = PaymentStatus.Refunded;

        usdc.safeTransfer(depositor, amount);

        emit PaymentRefunded(paymentId, depositor, amount);
    }

    /**
     * @notice Annulation hors fenêtre gratuite : pénalité distribuée aux bénéficiaires,
     *         solde remboursé au client.
     *
     *         Calcul :
     *           pénalité      = amount × penaltyBps / 10 000
     *           remboursement = amount − pénalité
     *           pénalité distribuée aux bénéficiaires à leur pro-rata
     *
     * @param penaltyBps Taux de pénalité en basis points (configuré dans la config partenaire côté Java)
     */
    function distributeAndRefund(bytes32 paymentId, uint16 penaltyBps)
        external
        onlyRole(RELAYER_ROLE)
        whenNotPaused
        nonReentrant
    {
        Payment storage pmt = _payments[paymentId];
        require(pmt.depositor != address(0),                       "Payment not found");
        require(pmt.status == PaymentStatus.Pending,               "Payment not pending");
        require(penaltyBps > 0 && penaltyBps < BPS_DENOMINATOR,   "Invalid penalty BPS");

        ContractConfig storage cfg = _configs[pmt.configId];
        uint256 total        = pmt.amount;
        uint256 penaltyAmt   = (total * penaltyBps) / BPS_DENOMINATOR;
        uint256 refundAmt    = total - penaltyAmt;

        pmt.status = PaymentStatus.PartialRefund;

        // Remboursement net au client
        usdc.safeTransfer(pmt.depositor, refundAmt);

        // Distribution de la pénalité aux bénéficiaires au pro-rata
        uint256 remaining = penaltyAmt;
        uint256 len       = cfg.beneficiaries.length;

        for (uint256 i; i < len; i++) {
            Beneficiary memory b = cfg.beneficiaries[i];
            uint256 share = (i == len - 1)
                ? remaining
                : (penaltyAmt * b.sharesBps) / BPS_DENOMINATOR;

            remaining -= share;
            usdc.safeTransfer(b.wallet, share);
        }

        emit PaymentPartialRefund(paymentId, pmt.depositor, refundAmt, penaltyAmt);
    }

    // -------------------------------------------------------------------------
    // Emergency — DEFAULT_ADMIN_ROLE
    // -------------------------------------------------------------------------

    function pause()   external onlyRole(DEFAULT_ADMIN_ROLE) { _pause(); }
    function unpause() external onlyRole(DEFAULT_ADMIN_ROLE) { _unpause(); }

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------

    function getPayment(bytes32 paymentId)
        external view
        returns (
            bytes32       configId,
            address       depositor,
            uint256       amount,
            PaymentStatus status
        )
    {
        Payment storage p = _payments[paymentId];
        return (p.configId, p.depositor, p.amount, p.status);
    }

    function getConfig(bytes32 configId)
        external view
        returns (bool active, Beneficiary[] memory beneficiaries)
    {
        ContractConfig storage c = _configs[configId];
        return (c.active, c.beneficiaries);
    }
}
