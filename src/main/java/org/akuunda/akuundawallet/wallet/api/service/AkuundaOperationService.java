package org.akuunda.akuundawallet.wallet.api.service;

import org.akuunda.akuundawallet.transfert.api.dto.OperationUpdateDto;
import org.akuunda.akuundawallet.wallet.api.dto.OperationDto;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

public interface AkuundaOperationService {

    /**
        * Saves an operation to the database.
        *
        * @param operation the operation to save
        */
    ResponseEntity<String> saveOperation(Operation operation);

    /**
        * Retrieves an operation by its ID.
        *
        * @param operationId the ID of the operation to retrieve
        * @return a ResponseEntity containing the operation if found, or an error response if not found
        */
    ResponseEntity<OperationDto> getOperation(Long operationId);

    /**
        * Retrieves an operation by its wallet ID and operation ID.
        *
        * @param username the ID of the wallet
        * @param type the ID of the operation to retrieve
        * @return a ResponseEntity containing the operation if found, or an error response if not found
        */
    ResponseEntity<Page<OperationDto>> getOperationsByUserAndType(String username, String type, String page, String size);

    /**
        * Retrieves all operations associated with a specific user.
        *
        * @param userId the ID of the user
        * @param page the page number for pagination
        * @param size the size of each page for pagination
        * @return a ResponseEntity containing a list of operations for the specified user
        */
    ResponseEntity<Page<OperationDto>> getAllOperationsByUser(String username, String page, String size);


    /**
        * Updates an existing operation.
        *
        * @param operationUpdateDto the DTO containing the updated operation details
        * @return a ResponseEntity indicating the result of the update operation
        */
    ResponseEntity<String> updateOperation(final OperationUpdateDto operationUpdateDto);
}
