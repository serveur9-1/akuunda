package org.akuunda.akuundawallet.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.DatabaseBackupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
/*
  Classe de planification pour la sauvegarde hebdomadaire de la base de données.
  Cette classe utilise Spring Scheduler pour exécuter une tâche planifiée chaque semaine.
 */
public class DatabaseBackupScheduler {

    private final DatabaseBackupService databaseBackupService;

    @Scheduled(cron = "${akuunda.db.backup.cron:0 0 0 * * SUN}")
    public void scheduleWeeklyBackup() {
        // Appel du service de sauvegarde de la base de données
        log.info("Démarrage de la sauvegarde hebdomadaire de la base de données");
        log.debug("Démarrage de la sauvegarde hebdomadaire de la base de données");
        databaseBackupService.backupDatabase();
        log.info("Sauvegarde hebdomadaire de la base de données terminée");
        log.debug("Sauvegarde hebdomadaire de la base de données terminée");
    }
}
