package org.akuunda.akuundawallet.backoffice.repository;

import org.akuunda.akuundawallet.backoffice.entity.ProReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProReportRepository extends JpaRepository<ProReport, UUID> {

    Page<ProReport> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail, Pageable pageable);
}
