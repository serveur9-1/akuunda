// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "forge-std/Test.sol";
import "../src/AkuundaConditionalPayment.sol";
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// @dev USDC mock — 6 decimals comme le vrai USDC Polygon
contract MockUSDC is ERC20 {
    constructor() ERC20("USD Coin", "USDC") {}
    function decimals() public pure override returns (uint8) { return 6; }
    function mint(address to, uint256 amount) external { _mint(to, amount); }
}

contract AkuundaConditionalPaymentTest is Test {

    AkuundaConditionalPayment public ctr;
    MockUSDC                  public usdc;

    address admin    = makeAddr("admin");
    address relayer  = makeAddr("relayer");
    address client   = makeAddr("client");
    address partnerA = makeAddr("partnerA");  // 70%
    address partnerB = makeAddr("partnerB");  // 20%
    address akuunda  = makeAddr("akuunda");   // 10%

    bytes32 constant CONFIG_ID  = keccak256("PC-HOTEL-001");
    bytes32 constant PAYMENT_ID = keccak256("PCP-1234567890");

    uint256 constant AMOUNT = 100 * 1e6; // 100 USDC

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    function setUp() public {
        usdc = new MockUSDC();
        ctr  = new AkuundaConditionalPayment(address(usdc), admin);

        vm.startPrank(admin);
        ctr.grantRole(ctr.RELAYER_ROLE(), relayer);

        address[] memory beneficiaries = new address[](3);
        beneficiaries[0] = partnerA;
        beneficiaries[1] = partnerB;
        beneficiaries[2] = akuunda;

        uint16[] memory shares = new uint16[](3);
        shares[0] = 7000; // 70%
        shares[1] = 2000; // 20%
        shares[2] = 1000; // 10%

        ctr.registerConfig(CONFIG_ID, beneficiaries, shares);
        vm.stopPrank();

        usdc.mint(client, AMOUNT * 10);
        vm.prank(client);
        usdc.approve(address(ctr), type(uint256).max);
    }

    // -------------------------------------------------------------------------
    // registerConfig
    // -------------------------------------------------------------------------

    function test_RegisterConfig_Success() public view {
        (bool active, AkuundaConditionalPayment.Beneficiary[] memory bens) = ctr.getConfig(CONFIG_ID);
        assertTrue(active);
        assertEq(bens.length, 3);
        assertEq(bens[0].wallet, partnerA);
        assertEq(bens[0].sharesBps, 7000);
    }

    function test_RegisterConfig_RevertIfSharesNotSum10000() public {
        address[] memory bens = new address[](2);
        bens[0] = partnerA;
        bens[1] = partnerB;
        uint16[] memory shares = new uint16[](2);
        shares[0] = 5000;
        shares[1] = 4000; // total = 9000, pas 10000

        vm.prank(admin);
        vm.expectRevert("Shares must sum to 10000 BPS");
        ctr.registerConfig(keccak256("BAD"), bens, shares);
    }

    function test_RegisterConfig_RevertIfDuplicate() public {
        address[] memory bens = new address[](1);
        bens[0] = partnerA;
        uint16[] memory shares = new uint16[](1);
        shares[0] = 10000;

        vm.prank(admin);
        vm.expectRevert("Config already active");
        ctr.registerConfig(CONFIG_ID, bens, shares);
    }

    // -------------------------------------------------------------------------
    // deposit
    // -------------------------------------------------------------------------

    function test_Deposit_Success() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        (bytes32 cfgId, address depositor, uint256 amount, AkuundaConditionalPayment.PaymentStatus status) =
            ctr.getPayment(PAYMENT_ID);

        assertEq(cfgId,    CONFIG_ID);
        assertEq(depositor, client);
        assertEq(amount,    AMOUNT);
        assertEq(uint8(status), uint8(AkuundaConditionalPayment.PaymentStatus.Pending));
        assertEq(usdc.balanceOf(address(ctr)), AMOUNT);
    }

    function test_Deposit_RevertIfDuplicate() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(client);
        vm.expectRevert("Payment already exists");
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);
    }

    function test_Deposit_RevertIfConfigInactive() public {
        vm.prank(admin);
        ctr.deactivateConfig(CONFIG_ID);

        vm.prank(client);
        vm.expectRevert("Config not active");
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);
    }

    // -------------------------------------------------------------------------
    // distribute
    // -------------------------------------------------------------------------

    function test_Distribute_Success() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(relayer);
        ctr.distribute(PAYMENT_ID);

        assertEq(usdc.balanceOf(partnerA), 70 * 1e6);  // 70%
        assertEq(usdc.balanceOf(partnerB), 20 * 1e6);  // 20%
        assertEq(usdc.balanceOf(akuunda),  10 * 1e6);  // 10%
        assertEq(usdc.balanceOf(address(ctr)), 0);

        (,,, AkuundaConditionalPayment.PaymentStatus status) = ctr.getPayment(PAYMENT_ID);
        assertEq(uint8(status), uint8(AkuundaConditionalPayment.PaymentStatus.Distributed));
    }

    function test_Distribute_HandlesRoundingDust() public {
        // 33 USDC → 33% chacun pour 3 bénéficiaires avec shares 3333/3333/3334
        address[] memory bens = new address[](3);
        bens[0] = makeAddr("b1");
        bens[1] = makeAddr("b2");
        bens[2] = makeAddr("b3");
        uint16[] memory shares = new uint16[](3);
        shares[0] = 3333;
        shares[1] = 3333;
        shares[2] = 3334;

        bytes32 cfg2 = keccak256("CFG-DUST");
        vm.prank(admin);
        ctr.registerConfig(cfg2, bens, shares);

        uint256 dustAmount = 10 * 1e6; // 10 USDC
        usdc.mint(client, dustAmount);
        bytes32 pmtDust = keccak256("PMT-DUST");

        vm.prank(client);
        ctr.deposit(pmtDust, cfg2, dustAmount);

        vm.prank(relayer);
        ctr.distribute(pmtDust);

        // Vérifier que tout le montant est distribué (pas de dust bloqué)
        uint256 total = usdc.balanceOf(bens[0]) + usdc.balanceOf(bens[1]) + usdc.balanceOf(bens[2]);
        assertEq(total, dustAmount);
        assertEq(usdc.balanceOf(address(ctr)), 0);
    }

    function test_Distribute_RevertIfNotRelayer() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(client);
        vm.expectRevert();
        ctr.distribute(PAYMENT_ID);
    }

    function test_Distribute_RevertIfAlreadyDistributed() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(relayer);
        ctr.distribute(PAYMENT_ID);

        vm.prank(relayer);
        vm.expectRevert("Payment not pending");
        ctr.distribute(PAYMENT_ID);
    }

    // -------------------------------------------------------------------------
    // refund
    // -------------------------------------------------------------------------

    function test_Refund_Success() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        uint256 balanceBefore = usdc.balanceOf(client);

        vm.prank(relayer);
        ctr.refund(PAYMENT_ID);

        assertEq(usdc.balanceOf(client), balanceBefore + AMOUNT);
        assertEq(usdc.balanceOf(address(ctr)), 0);

        (,,, AkuundaConditionalPayment.PaymentStatus status) = ctr.getPayment(PAYMENT_ID);
        assertEq(uint8(status), uint8(AkuundaConditionalPayment.PaymentStatus.Refunded));
    }

    function test_Refund_RevertIfNotRelayer() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(client);
        vm.expectRevert();
        ctr.refund(PAYMENT_ID);
    }

    // -------------------------------------------------------------------------
    // distributeAndRefund (pénalité)
    // -------------------------------------------------------------------------

    function test_DistributeAndRefund_Success() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        uint256 clientBalBefore = usdc.balanceOf(client);

        // Pénalité 10% = 10 USDC
        vm.prank(relayer);
        ctr.distributeAndRefund(PAYMENT_ID, 1000);

        // Client reçoit 90 USDC
        assertEq(usdc.balanceOf(client), clientBalBefore + 90 * 1e6);

        // Pénalité 10 USDC distribuée : partnerA 70% = 7, partnerB 20% = 2, akuunda 10% = 1
        assertEq(usdc.balanceOf(partnerA), 7 * 1e6);
        assertEq(usdc.balanceOf(partnerB), 2 * 1e6);
        assertEq(usdc.balanceOf(akuunda),  1 * 1e6);
        assertEq(usdc.balanceOf(address(ctr)), 0);

        (,,, AkuundaConditionalPayment.PaymentStatus status) = ctr.getPayment(PAYMENT_ID);
        assertEq(uint8(status), uint8(AkuundaConditionalPayment.PaymentStatus.PartialRefund));
    }

    function test_DistributeAndRefund_RevertIfPenalty0() public {
        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        vm.prank(relayer);
        vm.expectRevert("Invalid penalty BPS");
        ctr.distributeAndRefund(PAYMENT_ID, 0);
    }

    // -------------------------------------------------------------------------
    // Pause
    // -------------------------------------------------------------------------

    function test_Pause_BlocksDeposit() public {
        vm.prank(admin);
        ctr.pause();

        vm.prank(client);
        vm.expectRevert();
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);
    }

    function test_Unpause_RestoresDeposit() public {
        vm.prank(admin);
        ctr.pause();
        vm.prank(admin);
        ctr.unpause();

        vm.prank(client);
        ctr.deposit(PAYMENT_ID, CONFIG_ID, AMOUNT);

        (,, uint256 amount,) = ctr.getPayment(PAYMENT_ID);
        assertEq(amount, AMOUNT);
    }
}
