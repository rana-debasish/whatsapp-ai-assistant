package com.debasish.whatsappassistant.controller;
import com.debasish.whatsappassistant.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/webhook")
public class WhatsAppWebhookController {

    private static final String VERIFY_TOKEN = "whatsappassistant123";
    private static final String DISCONNECTED_MESSAGE =
            "Your WhatsApp account is not connected to the platform. Please connect your account from the dashboard to continue using the AI assistant.";
    private static final String AI_UNAVAILABLE_MESSAGE =
            "The AI assistant is temporarily unavailable. Please try again shortly.";
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private final Map<String, PendingEmailCommand> pendingEmailCommands =
            new ConcurrentHashMap<>();

    private final WhatsAppService whatsAppService;
    private final GeminiService geminiService;
    private final UserService userService;
    private final MessageService messageService;
    private final WhatsappConnectionService whatsappConnectionService;
    private final ComposioService composioService;

    public WhatsAppWebhookController(
            WhatsAppService whatsAppService,
            GeminiService geminiService,
            UserService userService,
            MessageService messageService,
            WhatsappConnectionService whatsappConnectionService,
            ComposioService composioService) {

        this.whatsAppService = whatsAppService;
        this.geminiService = geminiService;
        this.userService = userService;
        this.messageService = messageService;
        this.whatsappConnectionService = whatsappConnectionService;
        this.composioService = composioService;
    }

    @GetMapping
    public String verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
            return challenge;
        }

        return "Verification failed";
    }

    @PostMapping
    public String receiveMessage(@RequestBody String payload) {

        try {

            System.out.println("===== WEBHOOK RECEIVED =====");

            ObjectMapper mapper = new ObjectMapper();

            JsonNode root = mapper.readTree(payload);

            JsonNode messages = root.path("entry")
                    .path(0)
                    .path("changes")
                    .path(0)
                    .path("value")
                    .path("messages");

            if (messages.isMissingNode()
                    || !messages.isArray()
                    || messages.isEmpty()) {

                System.out.println(
                        "Webhook received without user message."
                );

                return "EVENT_RECEIVED";
            }

            if (messages.isArray() && messages.size() > 0) {

                System.out.println("===== WHATSAPP MESSAGE =====");

                JsonNode firstMessage = messages.get(0);

                String messageType = firstMessage
                        .path("type")
                        .asText();

                if (!"text".equals(messageType)
                        || firstMessage.path("text").isMissingNode()
                        || firstMessage
                        .path("text")
                        .path("body")
                        .asText("")
                        .isBlank()) {

                    System.out.println(
                            "Ignored non-text or blank WhatsApp message. Type: "
                                    + messageType
                    );

                    return "EVENT_RECEIVED";
                }

                String message = firstMessage
                        .path("text")
                        .path("body")
                        .asText();

                String sender = firstMessage
                        .path("from")
                        .asText();

                userService.saveUser(sender);

                System.out.println("User Message: " + message);
                System.out.println("Sender: " + sender);

                if (message.trim().toUpperCase().startsWith("WA-")) {

                    boolean connected =
                            whatsappConnectionService.connect(
                                    message,
                                    sender
                            );

                    String reply = connected
                            ? "WhatsApp connected successfully."
                            : "Invalid WhatsApp connection code.";

                    whatsAppService.sendMessage(sender, reply);

                    System.out.println(
                            "Connection Reply: " + reply
                    );

                    return "EVENT_RECEIVED";
                }

                if (!whatsappConnectionService.isConnected(sender)) {

                    whatsAppService.sendMessage(
                            sender,
                            DISCONNECTED_MESSAGE
                    );

                    System.out.println(
                            "Disconnected user message ignored."
                    );

                    return "EVENT_RECEIVED";
                }

                Optional<String> pendingEmailReply =
                        handlePendingEmailConfirmation(
                                sender,
                                message
                        );

                if (pendingEmailReply.isPresent()) {

                    messageService.saveMessage(
                            sender,
                            message,
                            pendingEmailReply.get()
                    );

                    whatsAppService.sendMessage(
                            sender,
                            pendingEmailReply.get()
                    );

                    return "EVENT_RECEIVED";
                }

                Optional<String> composioReply =
                        handleComposioCommand(sender, message);

                if (composioReply.isPresent()) {

                    messageService.saveMessage(
                            sender,
                            message,
                            composioReply.get()
                    );

                    whatsAppService.sendMessage(
                            sender,
                            composioReply.get()
                    );

                    System.out.println(
                            "Composio Reply: " + composioReply.get()
                    );

                    return "EVENT_RECEIVED";
                }

                if (isGreetingOnly(message)) {

                    String reply = "How can I help you?";

                    messageService.saveMessage(
                            sender,
                            message,
                            reply
                    );

                    whatsAppService.sendMessage(sender, reply);

                    System.out.println("Greeting Reply: " + reply);

                    return "EVENT_RECEIVED";
                }

                String history =
                        messageService
                                .getConversationHistory(
                                        sender);

                System.out.println(
                        "========== HISTORY =========="
                );

                System.out.println(history);

                System.out.println(
                        "============================="
                );

                String prompt = """
You are a WhatsApp assistant.

Rules:
- Maximum 15 words.
- Direct answer only.
- No greetings.
- Remember previous conversation.
- Answer the current message only.
- Do not continue an old task unless the current message clearly asks for it.

Conversation History:
%s

Current User Message:
%s
""".formatted(history, message);

                System.out.println(
                        "========== PROMPT =========="
                );

                System.out.println(prompt);

                System.out.println(
                        "============================"
                );

                String reply =
                        geminiService.askGemini(prompt);

                if (reply == null
                        || reply.isBlank()
                        || reply.contains("Error talking to Gemini")) {

                    reply = AI_UNAVAILABLE_MESSAGE;
                }

                messageService.saveMessage(
                        sender,
                        message,
                        reply
                );

                whatsAppService.sendMessage(sender, reply);

                System.out.println("Gemini Reply: " + reply);
            }

        } catch (Exception e) {
            System.out.println(
                    "Webhook processing failed: " + e.getMessage()
            );
        }

        return "EVENT_RECEIVED";
    }

    private Optional<String> handleComposioCommand(
            String sender,
            String message) {

        String lowerMessage = message.toLowerCase();

        if (isGmailCommand(lowerMessage)) {
            if (isSendEmailCommand(lowerMessage)) {
                String emailCommand =
                        buildEmailSendCommand(message);

                pendingEmailCommands.put(
                        sender,
                        new PendingEmailCommand(
                                emailCommand,
                                LocalDateTime.now()
                        )
                );

                return Optional.of(
                        buildEmailPreviewReply(emailCommand)
                );
            }

            return executeComposioCommand(
                    sender,
                    message,
                    "gmail",
                    getGmailTool(lowerMessage),
                    "Please connect Gmail from the dashboard before sending email commands."
            );
        }

        if (isCalendarCommand(lowerMessage)) {
            return executeComposioCommand(
                    sender,
                    message,
                    "googlecalendar",
                    getCalendarTool(lowerMessage),
                    "Please connect Google Calendar from the dashboard before using calendar commands."
            );
        }

        return Optional.empty();
    }

    private Optional<String> handlePendingEmailConfirmation(
            String sender,
            String message) {

        PendingEmailCommand pendingCommand =
                pendingEmailCommands.get(sender);

        if (pendingCommand == null) {
            return Optional.empty();
        }

        if (pendingCommand.createdAt().isBefore(
                LocalDateTime.now().minusMinutes(5))) {

            pendingEmailCommands.remove(sender);
            return Optional.empty();
        }

        String normalizedMessage =
                message.trim().toLowerCase();

        if ("yes".equals(normalizedMessage)
                || "send".equals(normalizedMessage)
                || "confirm".equals(normalizedMessage)) {

            pendingEmailCommands.remove(sender);

            return executeComposioCommand(
                    sender,
                    pendingCommand.command(),
                    "gmail",
                    "GMAIL_SEND_EMAIL",
                    "Please connect Gmail from the dashboard before sending email commands."
            );
        }

        if ("no".equals(normalizedMessage)
                || "cancel".equals(normalizedMessage)) {

            pendingEmailCommands.remove(sender);

            return Optional.of("Email cancelled.");
        }

        return Optional.empty();
    }

    private String buildEmailSendCommand(String message) {

        Matcher matcher = EMAIL_PATTERN.matcher(message);

        if (!matcher.find()) {
            return message;
        }

        String recipient = matcher.group();
        String draftPrompt = """
Write a professional email from this WhatsApp instruction.

Rules:
- Return exactly two lines.
- Line 1 format: Subject: ...
- Line 2 format: Body: ...
- Keep it warm and concise.

Instruction:
%s
""".formatted(message);

        String draft = geminiService.askGemini(draftPrompt);

        if (draft == null
                || draft.isBlank()
                || draft.contains("Error talking to Gemini")) {

            draft = "Subject: Hello\nBody: Best wishes.";
        }

        return "Send an email to "
                + recipient
                + " with this content:\n"
                + draft;
    }

    private String buildEmailPreviewReply(String emailCommand) {

        return """
Email draft ready:
%s

Reply YES to send it, or NO to cancel.
""".formatted(emailCommand);
    }

    private Optional<String> executeComposioCommand(
            String sender,
            String message,
            String toolkit,
            String toolSlug,
            String notConnectedMessage) {

        Optional<String> email =
                whatsappConnectionService.getConnectedEmail(sender);

        if (email.isEmpty()) {
            return Optional.of(DISCONNECTED_MESSAGE);
        }

        Optional<String> reply =
                composioService.executeToolWithText(
                        email.get(),
                        toolkit,
                        toolSlug,
                        message
                );

        return reply.or(() -> Optional.of(notConnectedMessage));
    }

    private boolean isGmailCommand(String message) {

        return message.contains("gmail")
                || message.contains("gamil")
                || message.contains("email")
                || message.contains("e-mail")
                || containsWord(message, "mail")
                || message.contains("mails")
                || message.contains("inbox")
                || message.contains("unread")
                || message.contains("latest mail")
                || message.contains("latest email")
                || message.contains("recent mail")
                || message.contains("recent email")
                || message.contains("summarize mail")
                || message.contains("summarize email")
                || message.contains("summerize mail")
                || message.contains("summerize email")
                || message.contains("read mail")
                || message.contains("read email");
    }

    private boolean isSendEmailCommand(String message) {

        return message.contains("send")
                || message.contains("compose")
                || message.contains("draft")
                || message.contains("write");
    }

    private boolean isGreetingOnly(String message) {

        String normalized = message
                .trim()
                .toLowerCase()
                .replaceAll("[!?., ]+", "");

        return "hi".equals(normalized)
                || "hello".equals(normalized)
                || "hey".equals(normalized)
                || "helo".equals(normalized);
    }

    private boolean containsWord(String message, String word) {

        return Pattern
                .compile("\\b" + Pattern.quote(word) + "\\b")
                .matcher(message)
                .find();
    }

    private boolean isCalendarCommand(String message) {

        return message.contains("calendar")
                || message.contains("calender")
                || message.contains("meeting")
                || message.contains("event")
                || message.contains("schedule")
                || message.contains("remind");
    }

    private String getGmailTool(String message) {

        if (isSendEmailCommand(message)) {
            return "GMAIL_SEND_EMAIL";
        }

        return "GMAIL_FETCH_EMAILS";
    }

    private String getCalendarTool(String message) {

        if (message.contains("delete")
                || message.contains("remove")
                || message.contains("cancel")) {
            return "GOOGLECALENDAR_DELETE_EVENT";
        }

        if (message.contains("set")
                || message.contains("add")
                || message.contains("create")
                || message.contains("schedule")
                || message.contains("book")
                || message.contains("remind")) {
            return "GOOGLECALENDAR_QUICK_ADD";
        }

        return "GOOGLECALENDAR_EVENTS_LIST";
    }

    private record PendingEmailCommand(
            String command,
            LocalDateTime createdAt) {
    }
}
