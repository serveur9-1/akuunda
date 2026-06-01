package org.akuunda.akuundawallet.keycloak.api.service;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.keycloak.api.entities.Attachments;
import org.springframework.web.multipart.MultipartFile;

import java.util.stream.Stream;

public interface AttachmentServices {

    /**
     * {@inheritDoc}
     */
    Attachments saveFile(MultipartFile file, @Valid String userName, @Valid String typePiece, @Valid String fileExtension);
    Stream<Attachments> getAllUserFiles(@Valid String userName);

    Stream<Attachments> getAllUserFilesByType(@Valid String userName, @Valid String typePiece);

}
