package org.akuunda.akuundawallet.wallet.api.dao;

import org.akuunda.akuundawallet.wallet.api.entities.TransfiUserMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Optional;

@CrossOrigin("*")
@RepositoryRestResource(exported = false)
public interface TransfiUserMappingRepository extends JpaRepository<TransfiUserMapping, Long> {

    Optional<TransfiUserMapping> findByUsername(String username);

    Optional<TransfiUserMapping> findByTransfiUserId(String transfiUserId);
}
