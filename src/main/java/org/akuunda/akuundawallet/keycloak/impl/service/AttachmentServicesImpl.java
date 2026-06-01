package org.akuunda.akuundawallet.keycloak.impl.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.KeycloakConstants;
import org.akuunda.akuundawallet.common.utils.ImageUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.AttachmentRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Attachments;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.AttachmentServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class AttachmentServicesImpl implements AttachmentServices {

    @Value("${upload.path}")
    private String uploadPath;
    private final AttachmentRepository attachmentRepository;
    private final UserRepository userRepository;


    /**
     * {@inheritDoc}
     */
    @Override
    public Attachments saveFile(MultipartFile file, @Valid String userName, @Valid String typePiece, @Valid String fileExtension) {
        //log.info("create file with name : {} type piece : {} and user name : {} ", file.getOriginalFilename(), typePiece, userName);
        try {
            if (!file.isEmpty()) {
                Users user = userRepository.getUsersByUsername(userName);
                if (user.getUserId() != null) {
                    Instant instant = Instant.now();
                    long timeStampMillis = instant.toEpochMilli();
                    String name = user.getFirstname() + "_" + user.getLastname().replace(" ", "_");
                    final var fileName = typePiece + "_" + name + "_" + timeStampMillis + "." + fileExtension; // add a date and hour
                    saveFileInDirectory(file.getBytes(), userName, fileName);
                    return attachmentRepository.save(
                            Attachments.builder()
                                    .fileExtension(fileExtension)
                                    .fileName(fileName)
                                    .typePiece(typePiece)
                                    .users(user)
                                    .dateCreate(Timestamp.from(Instant.now()).toString())
                                    .build());
                } else {
                    // log.error("error to store file user not found with id : {} " , userName);
                    return new Attachments();
                }
            } else {
                // log.error("error to store file but is not found");
                return new Attachments();
            }
        } catch (Exception ex){
            // log.error("error to store file " + ex.getMessage());
            return new Attachments();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Stream<Attachments> getAllUserFiles(@Valid String userName) {
        //log.info("get all user files with userName : {} ", userName);
        try {
            Users user = userRepository.getUsersByUsername(userName);
            if (user.getUserId() != null) {
                final var filesUserName = attachmentRepository.findAttachmentsByUsers(user);
                //parcourir la liste des noms de fichiers pour recuperer les fichiers
                return getAttachmentsStream(userName, filesUserName);
            } else {
                return Stream.empty();
            }

        } catch (Exception ex){
            // log.error("error to get all files " + ex.getMessage());
            return Stream.empty();
        }
    }

    @Override
    public Stream<Attachments> getAllUserFilesByType(String userName, String typePiece) {
        // log.info("get all user files with userName : {} and typePiece : {} ", userName, typePiece);
        try {
            Users user = userRepository.getUsersByUsername(userName);
            if (user.getUserId() != null) {
                final var filesUserName = attachmentRepository.findAttachmentsByUsersAndTypePiece(user, typePiece);
                //parcourir la liste des noms de fichiers pour recuperer les fichiers
                return getAttachmentsStream(userName, filesUserName);
            } else {
                return Stream.empty();
            }

        } catch (Exception ex){
            //log.error("error to get all files " + ex.getMessage());
            return Stream.empty();
        }
    }

    private Stream<Attachments> getAttachmentsStream(String userName, List<Attachments> filesUserName) {
        List<Attachments> attachmentsList = new ArrayList<>();
        for (Attachments value : filesUserName) {
            Attachments attachment = new Attachments();
            attachment.setFileName(value.getFileName());
            attachment.setDateCreate(value.getDateCreate());
            attachment.setId(value.getId());
            attachment.setTypePiece(value.getTypePiece());
            attachment.setFileExtension(value.getFileExtension());
            attachment.setUsers(value.getUsers());
            attachment.setFileData(ImageUtil.decompressImage(getFileFromDirectory(value.getFileName(), userName)));
            attachmentsList.add(attachment);
        }
        return attachmentsList.stream();
    }

    void saveFileInDirectory(byte[] userFile, String userName, String fileName) {
        checkFileDirectory(userName);
        try {
            Files.write(Paths.get(System.getProperty("user.home") + KeycloakConstants.FILES_DIRECTORY + userName + "/" + fileName), ImageUtil.compressImage(userFile));

        } catch (IOException e) {
            //log.error("error in file saved in repertory ");
        }
    }

    byte[] getFileFromDirectory(String fileName, String name) {
        checkFileDirectory(name);
        try {
            return Files.readAllBytes(Paths.get(uploadPath + name + "/" + fileName));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private static void checkFileDirectory(String userName) {
        File file = new File(System.getProperty("user.home") + KeycloakConstants.FILES_DIRECTORY + userName);
        if(!file.exists()) {
            if(file.mkdirs()) {
                System.out.println("Directory is created!");
                // log.info("repertoire de l'utilisateur {} creer ", userName);
            } else {
                //log.error("erreur creation repertoire de utilisateur {} ", userName);
            }
        }
    }

}
