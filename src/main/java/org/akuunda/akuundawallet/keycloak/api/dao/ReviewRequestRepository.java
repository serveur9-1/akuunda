package org.akuunda.akuundawallet.keycloak.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.ReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRequestRepository extends JpaRepository<ReviewRequest, Long> {
}
