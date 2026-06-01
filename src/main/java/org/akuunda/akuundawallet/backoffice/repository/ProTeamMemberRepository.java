package org.akuunda.akuundawallet.backoffice.repository;

import org.akuunda.akuundawallet.backoffice.entity.ProTeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProTeamMemberRepository extends JpaRepository<ProTeamMember, UUID> {

    List<ProTeamMember> findByOwnerEmailOrderByInvitedAtDesc(String ownerEmail);

    Optional<ProTeamMember> findByOwnerEmailAndEmail(String ownerEmail, String email);

    boolean existsByOwnerEmailAndEmail(String ownerEmail, String email);

    long countByOwnerEmail(String ownerEmail);

    long countByOwnerEmailAndStatus(String ownerEmail, String status);
}
