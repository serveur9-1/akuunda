package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.AkunndaMTargetSmsClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkunndaMTargetSmsClientServiceImpl implements AkunndaMTargetSmsClientService {

        @Value("${mtarget.url.public}")
        private String mTargetUrlBase;

        @Value("${mtarget.serviceId}")
        private String mTargetServiceId;

        @Value("${mtarget.username}")
        private String mTargetUsername;

        @Value("${mtarget.clientSecret}")
        private String mTargetPassword;

        @Value("${mtarget.sender}")
        private String mTargetSender;

        @Override
        public ResponseEntity<String> SendSimpleSms(final String msg, final String msisdn) {
                log.debug("SendSimpleSms({}, {})", msg, msisdn);
                log.info("SendSimpleSms({}, {})", msg, msisdn);
                String response;
                try {

                        String body = getSmsBody(mTargetUsername, mTargetSender, mTargetPassword, msg, msisdn);
                        final var httpResponse = HttpClientCall.SmsHttpPost(body, mTargetUrlBase + "messages");
                        response = httpResponse.body();

                        if (httpResponse.statusCode() != 200) {
                                return new ResponseEntity<>("Failed to send SMS. Error: " + response, HttpStatus.BAD_REQUEST);
                        }
                } catch (IOException | InterruptedException e) {
                        return new ResponseEntity<>("Failed to send SMS. Error: " + e.getMessage(), HttpStatus.BAD_REQUEST);
                }

                return new ResponseEntity<>(response, HttpStatus.OK);
        }

        @Override
        public void SendUnicodeSms(String msg, String msisdn) {
                String response;
                try {
                        String body = getSmsBody(mTargetUsername, mTargetSender, mTargetPassword, msg, msisdn);
                        final var httpResponse = HttpClientCall.SmsHttpPost(body, mTargetUrlBase + "messages");
                        response = httpResponse.body();

                        if (httpResponse.statusCode() != 200) {
                                log.error("Failed to send SMS. Error: " + response, HttpStatus.BAD_REQUEST);
                        }
                } catch (IOException | InterruptedException e) {
                        throw new IllegalArgumentException("Failed to send SMS. Error: " + e);
                }
        }

        private String getSmsBody(String username, String sender, String password, String msg, String msisdn) {
                String timeToSend = dateFormat();
                return "username=" + username + "&password=" + password + "&msisdn=" + msisdn + "&msg=" + msg + "&timetosend="
                        + timeToSend + "&sender=" + sender;
        }

        private String dateFormat() {
                String pattern = "YYYY-MM-dd HH:mm:ss";
                DateFormat df = new SimpleDateFormat(pattern);
                Date today = Calendar.getInstance().getTime();
                return df.format(today);
        }
}
