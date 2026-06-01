package org.akuunda.akuundawallet.common.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.DatabaseBackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class DatabaseBackupServiceImpl implements DatabaseBackupService {


    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String databaseUsername;

    @Value("${spring.datasource.password}")
    private String databasePassword;

    private static final String BACKUP_DIRECTORY = "C:/database-backups/";
    // You can change this to your desired backup directory
    private static final String BACKUP_FILE_NAME = "akuunda_backup.sql";

    @Override
    public ResponseEntity<String> backupDatabase() {
        LocalDate today = LocalDate.now();
        String formattedDate = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String backupPath = BACKUP_DIRECTORY + formattedDate;

        File backupDir = new File(backupPath);
        if (!backupDir.exists()) {
            boolean mkdirs = backupDir.mkdirs();
            if (!mkdirs) {
                log.debug("Erreur lors de la création du répertoire de sauvegarde : " + backupPath);
                log.info("Erreur lors de la création du répertoire de sauvegarde : " + backupPath);
                return new ResponseEntity<>("Erreur lors de la création du répertoire de sauvegarde.", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }

        String backupFile = backupPath + BACKUP_FILE_NAME;

        String host = databaseUrl.split("//")[1].split(":")[0]; // Extraction de l'hôte depuis l'URL
        String dbName = databaseUrl.split("/")[databaseUrl.split("/").length - 1]; // Extraction du nom de la base

        ProcessBuilder processBuilder = new ProcessBuilder(
                "pg_dump",
                "-h", host,
                "-U", databaseUsername,
                "-d", dbName,
                "-f", backupFile
        );

        processBuilder.environment().put("PGPASSWORD", databasePassword);

        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("Sauvegarde réussie : " + backupFile);
                log.debug("Sauvegarde réussie : " + backupFile);
                return ResponseEntity.status(HttpStatus.OK).body(backupFile);
            } else {
                log.debug("Erreur lors de la sauvegarde de la base de données.");
                log.info("Erreur lors de la sauvegarde de la base de données.");
                return new ResponseEntity<>("Erreur lors de la sauvegarde de la base de données. ",  HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IOException | InterruptedException e) {
            log.info("Erreur au niveau du backUp de la BD \n " + e.getMessage());
            log.debug("Erreur au niveau du backUp de la BD \n " + e.getMessage());
           return new ResponseEntity<>("Erreur au niveau du backUp de la BD \n " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

