package org.akuunda.akuundawallet.keycloak.api.dao;

import org.akuunda.akuundawallet.keycloak.api.entities.Attachments;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@CrossOrigin("*")
@RepositoryRestResource
public interface AttachmentRepository extends JpaRepository<Attachments,String> {
    List<Attachments> findAttachmentsByUsers(Users users);
    List<Attachments> findAttachmentsByUsersAndTypePiece(Users users, String typePiece);

}
