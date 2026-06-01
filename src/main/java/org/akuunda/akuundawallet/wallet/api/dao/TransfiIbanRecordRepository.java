package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.TransfiIbanRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource(exported = false)
public interface TransfiIbanRecordRepository extends JpaRepository<TransfiIbanRecord, Long> {

    Optional<TransfiIbanRecord> findByIbanId(String ibanId);

    List<TransfiIbanRecord> findByUsername(String username);

    List<TransfiIbanRecord> findByUsernameAndCurrency(String username, String currency);
}
