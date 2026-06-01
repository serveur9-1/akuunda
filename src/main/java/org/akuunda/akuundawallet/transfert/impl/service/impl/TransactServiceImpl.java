package org.akuunda.akuundawallet.transfert.impl.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.transfert.impl.service.TransactService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactServiceImpl implements TransactService {

    /*private final TransactRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Override
    public ResponseEntity<Void> saveTransaction(TransactionTransact transaction) {
        log.info("Saving transaction {}", transaction);
        log.debug("Saving transaction {}", transaction);

        // getWalletBy walletAddress
        Wallet wallet = walletRepository.findByAddress(transaction.getWalletAddress());
        if (wallet == null) {
            log.error("Wallet not found");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        // update wallet balance
        if (transaction.getIsBuyOrSell().equals("Sell")) { //vente on ramp
            wallet.setBalance(
                    wallet.getBalance()
                            + transaction.getCryptoAmount()); //Montant du paiement crypto attendu BTC, ETH etc..
            wallet.setDeviseBalance(
                    wallet.getDeviseBalance()
                            + transaction.getFiatAmount()); //Montant du paiement fiat attendu EURO, XOF etc..
            walletRepository.saveAndFlush(wallet);
        } else if (transaction.getIsBuyOrSell().equals("Buy")) { //achat  off ramp
            wallet.setBalance(
                    wallet.getBalance()
                            - transaction.getCryptoAmount());  //Montant du paiement crypto attendu BTC, ETH etc..
            wallet.setDeviseBalance(
                    wallet.getDeviseBalance()
                            - transaction.getFiatAmount()); //Montant du paiement fiat attendu EURO, XOF etc..
            walletRepository.saveAndFlush(wallet);
        } else {
            log.error("Invalid transaction type");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        transactionRepository.save(transaction);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }*/
}
