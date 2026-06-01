package org.akuunda.akuundawallet.esim.api.dao;

import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerial;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerialStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EsimSimSerialRepository extends JpaRepository<EsimSimSerial, Long> {
    Optional<EsimSimSerial> findBySimSerial(String simSerial);
    Optional<EsimSimSerial> findFirstByStatusOrderByIdAsc(EsimSimSerialStatus status);
    Optional<EsimSimSerial> findFirstByAssignedUserIdAndStatusOrderByIdAsc(String assignedUserId, EsimSimSerialStatus status);
    Optional<EsimSimSerial> findFirstByAssignedUserIdAndLastProductIdIsNotNullOrderByIdDesc(String assignedUserId);
    Optional<EsimSimSerial> findFirstByAssignedUserIdOrderByIdDesc(String assignedUserId);
    List<EsimSimSerial> findAllByStatusOrderByIdAsc(EsimSimSerialStatus status);
    List<EsimSimSerial> findAllByOrderByIdAsc();
}
