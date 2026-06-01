package org.akuunda.akuundawallet.common.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.DatabaseBackupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/database-backup")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Akunnda - DATABASE BACKUP", description = "Controller for database backup operations")
public class DatabaseBackupController {

    private final DatabaseBackupService databaseBackupService;

    @GetMapping("/backup")
    public ResponseEntity<String> backupDatabase() {
        log.info("Backup database");
        log.debug("Backup database");
        return databaseBackupService.backupDatabase();
    }
}
