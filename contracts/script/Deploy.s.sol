// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "forge-std/Script.sol";
import "../src/AkuundaConditionalPayment.sol";

/**
 * @notice Script de déploiement sur Polygon Mainnet.
 *
 * Variables d'environnement requises :
 *   DEPLOYER_PRIVATE_KEY  — clé privée du wallet de déploiement
 *   ADMIN_ADDRESS         — adresse du wallet admin Akuunda
 *   RELAYER_ADDRESS       — adresse du wallet service Akuunda (backend Java)
 *   USDC_ADDRESS          — 0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359 (Polygon)
 *
 * Commande :
 *   forge script script/Deploy.s.sol \
 *     --rpc-url $POLYGON_RPC_URL \
 *     --broadcast \
 *     --verify \
 *     --etherscan-api-key $POLYGONSCAN_API_KEY
 */
contract Deploy is Script {

    // USDC natif Polygon Mainnet
    address constant USDC_POLYGON = 0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359;

    function run() external {
        uint256 deployerKey     = vm.envUint("DEPLOYER_PRIVATE_KEY");
        address adminAddress    = vm.envAddress("ADMIN_ADDRESS");
        address relayerAddress  = vm.envAddress("RELAYER_ADDRESS");
        address usdcAddress     = vm.envOr("USDC_ADDRESS", USDC_POLYGON);

        vm.startBroadcast(deployerKey);

        AkuundaConditionalPayment contractDeployed = new AkuundaConditionalPayment(
            usdcAddress,
            adminAddress
        );

        // Accorder le rôle RELAYER au wallet service Java
        contractDeployed.grantRole(contractDeployed.RELAYER_ROLE(), relayerAddress);

        vm.stopBroadcast();

        console.log("AkuundaConditionalPayment deployed at:", address(contractDeployed));
        console.log("Admin  :", adminAddress);
        console.log("Relayer:", relayerAddress);
        console.log("USDC   :", usdcAddress);
    }
}
