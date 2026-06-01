package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class SftpSyncRequestDto {

    /** Override esim.sftp.host if provided. */
    private String host;

    /** Override esim.sftp.port if provided (default 22). */
    private Integer port;

    /** Override esim.sftp.username if provided. */
    private String username;

    /** Override esim.sftp.password if provided. */
    private String password;

    /** Override esim.sftp.remote-dir if provided. */
    private String remoteDir;

    /** Override esim.sftp.file-pattern if provided. */
    private String filePattern;
}
