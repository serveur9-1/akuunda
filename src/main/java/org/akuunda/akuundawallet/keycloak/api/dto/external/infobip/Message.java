package org.akuunda.akuundawallet.keycloak.api.dto.external.infobip;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Message {

    private String from;
    private ArrayList<Destination> destinations;
    private String text;
}
