package com.debasish.whatsappassistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class WhatsAppService {

    @Value("${whatsapp.access-token}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id}")
    private String phoneNumberId;

    public void sendMessage(String to, String message) {

        String url =
                "https://graph.facebook.com/v23.0/"
                        + phoneNumberId
                        + "/messages";

        WebClient webClient = WebClient.builder().build();

        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of(
                        "body", message
                )
        );

        String response = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        System.out.println("WhatsApp Response: " + response);
    }
}