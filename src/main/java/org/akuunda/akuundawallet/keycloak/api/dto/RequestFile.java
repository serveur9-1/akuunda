package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Data;
import lombok.ToString;
import org.springframework.web.multipart.MultipartFile;

@Data
@ToString
public class RequestFile {

    private MultipartFile file;
    private String typePiece;
}
