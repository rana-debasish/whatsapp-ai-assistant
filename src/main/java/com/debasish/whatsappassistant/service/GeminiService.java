package com.debasish.whatsappassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api-key}")
    private String apiKey;

    public String askGemini(String prompt) {

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
                        + apiKey;

        WebClient webClient = WebClient.builder().build();

        Map<String, Object> body = Map.of(
                "contents",
                new Object[]{
                        Map.of(
                                "parts",
                                new Object[]{
                                        Map.of("text", prompt)
                                }
                        )
                }
        );

        try {

            String response = webClient.post()
                    .uri(url)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            System.out.println("RAW GEMINI RESPONSE:");
            System.out.println(response);

            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(response);

            String answer = root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();

            System.out.println("EXTRACTED ANSWER:");
            System.out.println(answer);

            return answer;

        } catch (Exception e) {

            System.out.println(
                    "Gemini request failed: " + e.getMessage()
            );

            return "Sorry, AI service is temporarily unavailable.";
        }
    }
}
