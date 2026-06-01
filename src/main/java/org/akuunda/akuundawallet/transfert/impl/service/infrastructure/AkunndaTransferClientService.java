package org.akuunda.akuundawallet.transfert.impl.service.infrastructure;

import jakarta.validation.Valid;
import org.akuunda.akuundawallet.transfert.api.dto.command.TransactionCommand;
import org.akuunda.akuundawallet.transfert.api.dto.external.TransactionResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkunndaTransferClientService {

    /**
     * {@inheritDoc}
     */
    String getAllTransaction(@Valid final TransactionCommand command);

    /**
     * {@inheritDoc}
     */
    String executeNftTransfer(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<TransactionResponseDto> executeTransfert(@Valid final String body, final String headerPin);

    /**
     * {@inheritDoc}
     */
    String cancelTransaction(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    String readContract(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    String createContract(@Valid final String body);

    /**
     * {@inheritDoc}
     */
    String checkContractStatus(@Valid final String deployment_id);

    /**
     * {@inheritDoc}
     */
    String getContractByChainAndAddress(@Valid final String chain, @Valid final String contractAddress);

    /**
     * {@inheritDoc}
     */
    String deleteContract(@Valid final String chain, @Valid final String contractAddress);


}
