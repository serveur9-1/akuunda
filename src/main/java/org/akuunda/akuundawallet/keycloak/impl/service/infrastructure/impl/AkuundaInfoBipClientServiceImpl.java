package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.AkuundaInfoBipClientService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
public class AkuundaInfoBipClientServiceImpl implements AkuundaInfoBipClientService {

    @Value("${infobip.base.url}")
    private String infoBipUrlBase;

    @Value("${infobip.api.key}")
    private String infoBipApiKey;

    @Value("${infobip.expediteur}")
    private String infoBipExpediteur;


    @Override
    public ResponseEntity<String> SendSimpleSms(String msg, String msisdn) {
        String response;

        String body = getSmsBody(infoBipExpediteur, msg, msisdn);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(infoBipUrlBase))
                .header("accept", "application/json")
                .header("Authorization", "App " + infoBipApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            final var httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            final var statusCode = httpResponse.statusCode();
            response = httpResponse.body();

            if (statusCode != 200) {
                return new ResponseEntity<>(response, HttpStatusCode.valueOf(statusCode));
            }

        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>("Failed to send SMS. Error: " + e, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    private String getSmsBody(String infoBipExpediteur, String msg, String msisdn) {
        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        JSONArray destinations = new JSONArray();
        JSONObject destination = new JSONObject();

        destination.put("to", msisdn);
        destinations.put(destination);

        message.put("from", infoBipExpediteur);
        message.put("text", msg);
        message.put("destinations", destinations);

        messages.put(message);
        body.put("messages", messages);

        return body.toString();
    }
}
