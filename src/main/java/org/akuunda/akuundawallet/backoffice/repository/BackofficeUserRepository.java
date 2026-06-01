package org.akuunda.akuundawallet.backoffice.repository;

import org.akuunda.akuundawallet.backoffice.entity.BackofficeUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BackofficeUserRepository extends JpaRepository<BackofficeUser, UUID> {

    Optional<BackofficeUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
