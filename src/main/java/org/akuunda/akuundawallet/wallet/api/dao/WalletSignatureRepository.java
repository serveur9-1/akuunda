package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.MtPelerinTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.WalletSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletSignatureRepository extends JpaRepository<WalletSignature, Long> {

    List<WalletSignature> findWalletSignatureByUserName(String userName);
    WalletSignature findWalletSignatureByUserNameAndMtpCode(String userName, String mtpCode);
}
