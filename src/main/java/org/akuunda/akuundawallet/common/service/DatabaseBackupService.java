package org.akuunda.akuundawallet.common.service;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface DatabaseBackupService {

    ResponseEntity<String> backupDatabase();
}
