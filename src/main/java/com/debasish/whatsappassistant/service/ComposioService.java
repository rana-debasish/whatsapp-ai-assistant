package com.debasish.whatsappassistant.service;

import com.debasish.whatsappassistant.entity.ComposioConnection;
import com.debasish.whatsappassistant.repository.ComposioConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ComposioService {

    private static final String BASE_URL =
            "https://backend.composio.dev/api/v3.1";
    private static final String ACTIVE = "ACTIVE";
    private static final String INACTIVE = "INACTIVE";
    private static final String PENDING = "PENDING";

    private final ComposioConnectionRepository repository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${composio.api-key:}")
    private String apiKey;

    @Value("${composio.gmail-auth-config-id:}")
    private String gmailAuthConfigId;

    @Value("${composio.google-calendar-auth-config-id:}")
    private String googleCalendarAuthConfigId;

    public ComposioService(
            ComposioConnectionRepository repository) {

        this.repository = repository;
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .baseUrl(BASE_URL)
                .build();
    }

    public Map<String, Object> createConnectLink(
            String email,
            String toolkit,
            String callbackUrl) {

        String normalizedToolkit = normalizeToolkit(toolkit);
        String authConfigId = getAuthConfigId(normalizedToolkit);

        if (!isConfigured(apiKey)) {
            return Map.of(
                    "success", false,
                    "message", "Composio API key is not loaded. Restart the Spring Boot app after setting it."
            );
        }

        if (!isConfigured(authConfigId)) {
            return Map.of(
                    "success", false,
                    "message", "Composio is not configured for "
                            + displayName(normalizedToolkit)
            );
        }

        try {

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("auth_config_id", authConfigId);
            body.put("user_id", email);
            body.put("callback_url", callbackUrl);

            String responseBody = webClient.post()
                    .uri("/connected_accounts/link")
                    .header("x-api-key", apiKey)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseBody);

            String connectedAccountId = response
                    .path("connected_account_id")
                    .asText("");

            if (!connectedAccountId.isBlank()) {
                saveConnection(
                        email,
                        normalizedToolkit,
                        connectedAccountId,
                        PENDING
                );
            }

            return Map.of(
                    "success", true,
                    "redirectUrl", response
                            .path("redirect_url")
                            .asText()
            );

        } catch (WebClientResponseException e) {
            System.out.println(
                    "Composio connect link failed: "
                            + e.getStatusCode()
                            + " "
                            + e.getResponseBodyAsString()
            );

            return Map.of(
                    "success", false,
                    "message", getComposioErrorMessage(
                            e,
                            "Unable to start "
                                    + displayName(normalizedToolkit)
                                    + " connection right now."
                    )
            );

        } catch (Exception e) {
            System.out.println(
                    "Composio connect link failed: " + e.getMessage()
            );

            return Map.of(
                    "success", false,
                    "message", "Unable to start "
                            + displayName(normalizedToolkit)
                            + " connection right now. Restart the app and try again."
            );
        }
    }

    public Map<String, Object> getStatus(
            String email,
            String toolkit) {

        String normalizedToolkit = normalizeToolkit(toolkit);

        refreshActiveConnection(email, normalizedToolkit);

        Optional<ComposioConnection> connection =
                repository.findFirstByEmailAndToolkitOrderByIdDesc(
                        email,
                        normalizedToolkit
                );

        boolean connected =
                connection
                        .map(value -> ACTIVE.equals(value.getStatus()))
                        .orElse(false);

        return Map.of(
                "toolkit", normalizedToolkit,
                "connected", connected,
                "connectedAccountId",
                connected
                        ? connection.get().getConnectedAccountId()
                        : ""
        );
    }

    public void markConnectedFromCallback(
            String email,
            String toolkit,
            String connectedAccountId) {

        if (connectedAccountId == null
                || connectedAccountId.isBlank()) {
            return;
        }

        saveConnection(
                email,
                normalizeToolkit(toolkit),
                connectedAccountId,
                ACTIVE
        );
    }

    public Map<String, Object> disconnect(
            String email,
            String toolkit) {

        String normalizedToolkit = normalizeToolkit(toolkit);

        Optional<ComposioConnection> connection =
                repository.findFirstByEmailAndToolkitOrderByIdDesc(
                        email,
                        normalizedToolkit
                );

        if (connection.isEmpty()) {
            return Map.of("success", true);
        }

        String connectedAccountId =
                connection.get().getConnectedAccountId();

        if (isConfigured(apiKey)
                && connectedAccountId != null
                && !connectedAccountId.isBlank()) {

            try {
                webClient.patch()
                        .uri("/connected_accounts/{id}/status",
                                connectedAccountId)
                        .header("x-api-key", apiKey)
                        .header(HttpHeaders.CONTENT_TYPE,
                                MediaType.APPLICATION_JSON_VALUE)
                        .bodyValue(Map.of("enabled", false))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

            } catch (Exception e) {
                System.out.println(
                        "Composio disconnect failed: " + e.getMessage()
                );
            }
        }

        saveConnection(
                email,
                normalizedToolkit,
                connectedAccountId,
                INACTIVE
        );

        return Map.of("success", true);
    }

    public Optional<String> executeToolWithText(
            String email,
            String toolkit,
            String toolSlug,
            String text) {

        String normalizedToolkit = normalizeToolkit(toolkit);

        refreshActiveConnection(email, normalizedToolkit);

        Optional<ComposioConnection> connection =
                repository.findFirstByEmailAndToolkitAndStatusOrderByIdDesc(
                        email,
                        normalizedToolkit,
                        ACTIVE
                );

        if (connection.isEmpty()) {
            return Optional.empty();
        }

        try {

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("user_id", email);
            body.put("connected_account_id",
                    connection.get().getConnectedAccountId());

            if ("GMAIL_FETCH_EMAILS".equals(toolSlug)) {
                body.put(
                        "arguments",
                        buildGmailFetchArguments(text)
                );
            } else {
                body.put("text", text);
            }

            String responseBody = webClient.post()
                    .uri("/tools/execute/{toolSlug}", toolSlug)
                    .header("x-api-key", apiKey)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseBody);

            if (response.path("successful").asBoolean(false)) {
                return Optional.of(
                        buildSuccessMessage(
                                normalizedToolkit,
                                toolSlug,
                                response.path("data"),
                                text
                        )
                );
            }

            return Optional.of(
                    buildFailureMessage(
                            normalizedToolkit,
                            toolSlug
                    )
            );

        } catch (WebClientResponseException e) {
            System.out.println(
                    "Composio tool execution failed: "
                            + e.getStatusCode()
                            + " "
                            + e.getResponseBodyAsString()
            );

            return Optional.of(
                    getComposioErrorMessage(
                            e,
                            displayName(normalizedToolkit)
                                    + " is temporarily unavailable. Please try again shortly."
                    )
            );

        } catch (Exception e) {
            System.out.println(
                    "Composio tool execution failed: " + e.getMessage()
            );

            return Optional.of(
                    displayName(normalizedToolkit)
                            + " is temporarily unavailable. Please try again shortly."
            );
        }
    }

    public String normalizeToolkit(String toolkit) {

        if (toolkit == null) {
            return "";
        }

        return toolkit.trim().toLowerCase();
    }

    public String displayName(String toolkit) {

        return switch (normalizeToolkit(toolkit)) {
            case "gmail" -> "Gmail";
            case "googlecalendar" -> "Google Calendar";
            default -> "Composio";
        };
    }

    private void refreshActiveConnection(
            String email,
            String toolkit) {

        if (!isConfigured(apiKey)) {
            return;
        }

        try {

            String uri = UriComponentsBuilder
                    .fromPath("/connected_accounts")
                    .queryParam("user_ids", email)
                    .queryParam("toolkit_slugs", toolkit)
                    .queryParam("statuses", ACTIVE)
                    .queryParam("limit", 1)
                    .build()
                    .toUriString();

            String responseBody = webClient.get()
                    .uri(uri)
                    .header("x-api-key", apiKey)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode response = objectMapper.readTree(responseBody);

            JsonNode firstItem = response
                    .path("items")
                    .isArray()
                    && response.path("items").size() > 0
                    ? response.path("items").get(0)
                    : null;

            if (firstItem != null) {
                saveConnection(
                        email,
                        toolkit,
                        firstItem.path("id").asText(),
                        firstItem.path("status").asText(ACTIVE)
                );
            }

        } catch (Exception e) {
            System.out.println(
                    "Composio status refresh failed: " + e.getMessage()
            );
        }
    }

    private void saveConnection(
            String email,
            String toolkit,
            String connectedAccountId,
            String status) {

        ComposioConnection connection =
                repository.findFirstByEmailAndToolkitOrderByIdDesc(
                                email,
                                toolkit
                        )
                        .orElseGet(ComposioConnection::new);

        connection.setEmail(email);
        connection.setToolkit(toolkit);
        connection.setConnectedAccountId(connectedAccountId);
        connection.setStatus(status);
        connection.setUpdatedAt(LocalDateTime.now());

        repository.save(connection);
    }

    private String getAuthConfigId(String toolkit) {

        return switch (normalizeToolkit(toolkit)) {
            case "gmail" -> gmailAuthConfigId;
            case "googlecalendar" -> googleCalendarAuthConfigId;
            default -> "";
        };
    }

    private boolean isConfigured(String value) {

        return value != null && !value.isBlank();
    }

    private String buildSuccessMessage(
            String toolkit,
            String toolSlug,
            JsonNode data,
            String requestText) {

        if ("googlecalendar".equals(toolkit)) {

            if ("GOOGLECALENDAR_QUICK_ADD".equals(toolSlug)
                    || "GOOGLECALENDAR_CREATE_EVENT".equals(toolSlug)) {
                return "Done. Calendar event created.";
            }

            if ("GOOGLECALENDAR_EVENTS_LIST".equals(toolSlug)
                    || "GOOGLECALENDAR_FIND_EVENT".equals(toolSlug)) {
                return summarizeCalendarEvents(data);
            }

            if ("GOOGLECALENDAR_DELETE_EVENT".equals(toolSlug)) {
                return "Done. Calendar event deleted.";
            }
        }

        if ("gmail".equals(toolkit)) {

            if ("GMAIL_SEND_EMAIL".equals(toolSlug)) {
                return "Done. Email sent.";
            }

            if ("GMAIL_FETCH_EMAILS".equals(toolSlug)) {
                return summarizeGmailEmails(data, requestText);
            }
        }

        return "Done.";
    }

    private Map<String, Object> buildGmailFetchArguments(String text) {

        String lowerText = text == null
                ? ""
                : text.toLowerCase();

        Map<String, Object> arguments =
                new LinkedHashMap<>();

        arguments.put("user_id", "me");
        arguments.put("max_results", 3);
        arguments.put("verbose", true);
        arguments.put("include_payload", true);

        if (lowerText.contains("unread")) {
            arguments.put(
                    "label_ids",
                    java.util.List.of("INBOX", "UNREAD")
            );
        } else {
            arguments.put(
                    "label_ids",
                    java.util.List.of("INBOX")
            );
        }

        return arguments;
    }

    private String buildFailureMessage(
            String toolkit,
            String toolSlug) {

        if ("googlecalendar".equals(toolkit)
                && "GOOGLECALENDAR_DELETE_EVENT".equals(toolSlug)) {
            return "I could not delete that event. Please include the exact event title and date.";
        }

        if ("googlecalendar".equals(toolkit)) {
            return "I could not complete that calendar action. Please include the event title, date, and time.";
        }

        if ("gmail".equals(toolkit)) {
            return "I could not complete that Gmail action. Please include the recipient and message.";
        }

        return "I could not complete that action. Please try with more details.";
    }

    private String summarizeCalendarEvents(JsonNode data) {

        JsonNode items = data.path("items");

        if (!items.isArray() || items.isEmpty()) {
            return "No calendar events found.";
        }

        StringBuilder reply =
                new StringBuilder("Calendar events:");

        int count = Math.min(items.size(), 3);

        for (int index = 0; index < count; index++) {
            JsonNode item = items.get(index);

            String summary = item.path("summary")
                    .asText("Untitled event");

            String start = item.path("start")
                    .path("dateTime")
                    .asText(item.path("start")
                            .path("date")
                            .asText(""));

            reply.append("\n")
                    .append(index + 1)
                    .append(". ")
                    .append(summary);

            if (!start.isBlank()) {
                reply.append(" - ")
                        .append(formatCalendarDate(start));
            }
        }

        return reply.toString();
    }

    private String summarizeGmailEmails(
            JsonNode data,
            String requestText) {

        JsonNode emails = findEmailArray(data);

        if (!emails.isArray() || emails.isEmpty()) {
            return "No emails found.";
        }

        Optional<Integer> requestedIndex =
                getRequestedEmailIndex(requestText);

        if (requestedIndex.isPresent()) {
            int index = requestedIndex.get();

            if (index >= emails.size()) {
                return "I could not find that email in the recent results.";
            }

            return summarizeSingleEmail(
                    emails.get(index),
                    index + 1
            );
        }

        StringBuilder reply =
                new StringBuilder("Latest emails:");

        int count = Math.min(emails.size(), 3);

        for (int index = 0; index < count; index++) {
            JsonNode email = emails.get(index);

            String subject = firstText(
                    email,
                    "subject",
                    "Subject",
                    "title"
            );

            String from = firstText(
                    email,
                    "from",
                    "sender",
                    "sender_email",
                    "from_email"
            );

            String snippet = firstText(
                    email,
                    "snippet",
                    "body",
                    "preview"
            );

            reply.append("\n")
                    .append(index + 1)
                    .append(". ");

            if (!subject.isBlank()) {
                reply.append(subject);
            } else {
                reply.append("No subject");
            }

            if (!from.isBlank()) {
                reply.append(" - ")
                        .append(from);
            }

            if (!snippet.isBlank()) {
                reply.append(" - ")
                        .append(shorten(snippet, 80));
            }
        }

        return reply.toString();
    }

    private String summarizeSingleEmail(
            JsonNode email,
            int position) {

        String subject = firstText(
                email,
                "subject",
                "Subject",
                "title"
        );

        String from = firstText(
                email,
                "from",
                "sender",
                "sender_email",
                "from_email"
        );

        String content = firstText(
                email,
                "messageText",
                "text",
                "body",
                "snippet"
        );

        if (content.isBlank()
                && email.path("preview").isObject()) {
            content = firstText(
                    email.path("preview"),
                    "body",
                    "snippet"
            );
        }

        if (content.isBlank()) {
            content = "No readable email content was returned.";
        }

        return "Email " + position + ":\n"
                + "Subject: " + emptyFallback(subject, "No subject") + "\n"
                + "From: " + emptyFallback(from, "Unknown sender") + "\n"
                + "Summary: " + shorten(content, 500);
    }

    private Optional<Integer> getRequestedEmailIndex(String text) {

        String lowerText = text == null
                ? ""
                : text.toLowerCase();

        if (lowerText.contains("first")
                || lowerText.contains("1st")) {
            return Optional.of(0);
        }

        if (lowerText.contains("second")
                || lowerText.contains("2nd")) {
            return Optional.of(1);
        }

        if (lowerText.contains("third")
                || lowerText.contains("3rd")) {
            return Optional.of(2);
        }

        return Optional.empty();
    }

    private JsonNode findEmailArray(JsonNode data) {

        if (data == null || data.isMissingNode()) {
            return objectMapper.createArrayNode();
        }

        for (String key : new String[]{
                "messages",
                "emails",
                "items",
                "results",
                "data"
        }) {
            JsonNode value = data.path(key);

            if (value.isArray()) {
                return value;
            }
        }

        if (data.isArray()) {
            return data;
        }

        return objectMapper.createArrayNode();
    }

    private String firstText(
            JsonNode node,
            String... keys) {

        for (String key : keys) {
            String value = node.path(key).asText("");

            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String emptyFallback(
            String value,
            String fallback) {

        return value == null || value.isBlank()
                ? fallback
                : value;
    }

    private String shorten(String value, int maxLength) {

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "...";
    }

    private String formatCalendarDate(String value) {

        return value
                .replace("T", " ")
                .replace("+05:30", "")
                .replace(":00", "");
    }

    private String summarize(JsonNode data) {

        if (data == null || data.isMissingNode() || data.isNull()) {
            return "";
        }

        String value = data.toString();

        if (value.length() <= 600) {
            return value;
        }

        return value.substring(0, 600) + "...";
    }

    private String getComposioErrorMessage(
            WebClientResponseException e,
            String fallback) {

        String responseBody = e.getResponseBodyAsString();

        if (responseBody == null || responseBody.isBlank()) {
            return fallback;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);

            String message = root.path("error")
                    .path("message")
                    .asText("");

            if (!message.isBlank()) {
                return "Composio error: " + message;
            }

        } catch (Exception ignored) {
            return fallback;
        }

        return fallback;
    }
}
