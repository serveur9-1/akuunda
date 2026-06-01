package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.TransfiOrderRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource(exported = false)
public interface TransfiOrderRecordRepository extends JpaRepository<TransfiOrderRecord, Long> {

    Optional<TransfiOrderRecord> findByOrderId(String orderId);

    Page<TransfiOrderRecord> findByUsername(String username, Pageable pageable);

    Page<TransfiOrderRecord> findByUsernameAndOrderType(String username, String orderType, Pageable pageable);
}
