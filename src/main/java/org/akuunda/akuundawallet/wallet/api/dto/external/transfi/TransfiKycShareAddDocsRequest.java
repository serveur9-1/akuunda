package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Body de POST /v3/kyc/share/add-docs (multipart/form-data).
 *
 * Permet de partager des documents additionnels pour augmenter les limites
 * de transaction d'un utilisateur, ou pour répondre à des exigences pays-spécifiques.
 *
 * Tous les champs sont obligatoires côté TransFi.
 * additionalDocType : libellé du type de document (ex: bank_statement)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransfiKycShareAddDocsRequest {
    private String userId;
    private String additionalDocType;
    private MultipartFile additionalDoc;
}
