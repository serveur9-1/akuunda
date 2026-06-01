package org.akuunda.akuundawallet.common.Exceptions;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Error implements Serializable {

    private String code;
    private String message;
}
