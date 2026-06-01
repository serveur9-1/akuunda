package org.akuunda.akuundawallet.esim.impl.service;

import com.jcraft.jsch.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.esim.api.dto.EsimSimStockImportResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Connects to the Transatel SFTP server, finds the latest SIM delivery CSV
 * and imports it via {@link EsimCsvImportService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsimSftpSyncService {

    private final EsimCsvImportService csvImportService;

    @Value("${esim.sftp.host:}")
    private String sftpHost;

    @Value("${esim.sftp.port:22}")
    private int sftpPort;

    @Value("${esim.sftp.username:}")
    private String sftpUsername;

    @Value("${esim.sftp.password:}")
    private String sftpPassword;

    @Value("${esim.sftp.remote-dir:/}")
    private String remoteDir;

    @Value("${esim.sftp.file-pattern:_park.csv}")
    private String filePattern;

    /**
     * Connects to SFTP using configured properties, downloads the latest CSV and imports it.
     */
    public EsimSimStockImportResult syncFromSftp() {
        return syncFromSftp(null, null, null, null, null, null);
    }

    /**
     * Connects to SFTP with optional override parameters (useful for on-demand testing).
     * Any null parameter falls back to the configured application property.
     */
    public EsimSimStockImportResult syncFromSftp(String host, Integer port, String username,
                                                  String password, String remoteDirectory,
                                                  String pattern) {
        String h    = nonBlank(host,            sftpHost);
        int    p    = port != null ? port       : sftpPort;
        String u    = nonBlank(username,        sftpUsername);
        String pw   = nonBlank(password,        sftpPassword);
        String dir  = nonBlank(remoteDirectory, remoteDir);
        String pat  = nonBlank(pattern,         filePattern);

        if (h.isBlank()) {
            EsimSimStockImportResult r = new EsimSimStockImportResult();
            r.addError("SFTP non configuré (host manquant).");
            return r;
        }
        if (u.isBlank() || pw.isBlank()) {
            EsimSimStockImportResult r = new EsimSimStockImportResult();
            r.addError("SFTP non configuré (username ou password manquant).");
            return r;
        }

        Session session = null;
        ChannelSftp channel = null;

        try {
            log.info("Connexion SFTP {}@{}:{} dir={}", u, h, p, dir);
            session = buildSession(h, p, u, pw);
            session.connect(30_000);

            channel = (ChannelSftp) session.openChannel("sftp");
            channel.connect(15_000);

            String targetFile = findLatestCsvFile(channel, dir, pat);
            if (targetFile == null) {
                EsimSimStockImportResult r = new EsimSimStockImportResult();
                r.addError("Aucun fichier CSV trouvé dans '" + dir + "' avec le pattern '" + pat + "'.");
                return r;
            }

            log.info("Téléchargement SFTP: {}", targetFile);
            try (InputStream is = channel.get(targetFile)) {
                return csvImportService.importFromStream(is);
            }

        } catch (Exception e) {
            log.error("Erreur sync SFTP: {}", e.getMessage(), e);
            EsimSimStockImportResult r = new EsimSimStockImportResult();
            r.addError("Erreur connexion SFTP: " + e.getMessage());
            return r;
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    @SuppressWarnings("unchecked")
    private String findLatestCsvFile(ChannelSftp channel, String dir, String pattern) throws SftpException {
        String listDir = dir.isBlank() ? "." : dir;
        List<ChannelSftp.LsEntry> entries = channel.ls(listDir);
        List<String> matches = new ArrayList<>();

        for (ChannelSftp.LsEntry entry : entries) {
            String name = entry.getFilename();
            if (!entry.getAttrs().isDir() && name.toLowerCase().contains(pattern.toLowerCase())) {
                matches.add(name);
            }
        }

        if (matches.isEmpty()) return null;

        // Sort descending — date-prefixed filenames sort chronologically
        matches.sort((a, b) -> b.compareToIgnoreCase(a));
        String prefix = listDir.endsWith("/") ? listDir : listDir + "/";
        return prefix + matches.get(0);
    }

    private Session buildSession(String host, int port, String username, String password) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);

        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        config.put("PreferredAuthentications", "password,keyboard-interactive");
        session.setConfig(config);

        return session;
    }

    private String nonBlank(String override, String fallback) {
        return (override != null && !override.isBlank()) ? override : (fallback != null ? fallback : "");
    }
}
