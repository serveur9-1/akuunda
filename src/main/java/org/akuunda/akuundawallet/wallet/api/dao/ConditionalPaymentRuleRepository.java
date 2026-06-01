package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPaymentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource
public interface ConditionalPaymentRuleRepository extends JpaRepository<ConditionalPaymentRule, Long> {
    
    List<ConditionalPaymentRule> findByServiceTypeAndIsActiveTrue(String serviceType);
    
    @Query("SELECT r FROM ConditionalPaymentRule r WHERE r.serviceType = :serviceType AND r.isActive = true AND (r.vendor = :vendor OR r.vendor IS NULL) ORDER BY r.hoursBeforeService DESC")
    List<ConditionalPaymentRule> findApplicableRules(@Param("serviceType") String serviceType, @Param("vendor") Users vendor);
    
    Optional<ConditionalPaymentRule> findByServiceTypeAndHoursBeforeServiceAndVendor(String serviceType, Integer hoursBeforeService, Users vendor);
}

