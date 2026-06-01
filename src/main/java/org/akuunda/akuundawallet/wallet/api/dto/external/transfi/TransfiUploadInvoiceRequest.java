package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Body de POST /v3/invoices/create (multipart/form-data).
 *
 * Champs obligatoires : invoice (PDF, max 4MB), direction (deposit|withdraw),
 * userId (doit commencer par UX-).
 *
 * Optionnel : invoiceType (invoice | receipt | contract | commercial_document).
 *
 * La réponse renvoie un invoiceId qu'on peut ensuite passer au root level
 * d'un payload de création d'ordre Payin / Payout (champ
 * TransfiCreateOrderRequest.invoiceId).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransfiUploadInvoiceRequest {
    private String userId;
    private String direction;
    private String invoiceType;
    private MultipartFile invoice;
}
