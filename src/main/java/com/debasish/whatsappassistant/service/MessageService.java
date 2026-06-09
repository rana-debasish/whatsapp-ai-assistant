package com.debasish.whatsappassistant.service;

import com.debasish.whatsappassistant.entity.Message;
import com.debasish.whatsappassistant.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(
            MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public void saveMessage(
            String phoneNumber,
            String userMessage,
            String aiReply) {

        Message message = new Message();

        message.setPhoneNumber(phoneNumber);
        message.setUserMessage(userMessage);
        message.setAiReply(aiReply);
        message.setCreatedAt(LocalDateTime.now());

        messageRepository.save(message);
    }

    public String getConversationHistory(
            String phoneNumber) {

        List<Message> messages =
                messageRepository
                        .findTop5ByPhoneNumberOrderByIdDesc(
                                phoneNumber);

        StringBuilder history =
                new StringBuilder();

        for (Message msg : messages) {

            if (shouldSkipForAssistantHistory(
                    msg.getUserMessage(),
                    msg.getAiReply())) {
                continue;
            }

            history.append("User: ")
                    .append(msg.getUserMessage())
                    .append("\n");

            history.append("Assistant: ")
                    .append(msg.getAiReply())
                    .append("\n");
        }

        return history.toString();
    }

    private boolean shouldSkipForAssistantHistory(
            String userMessage,
            String aiReply) {

        String userText = userMessage == null
                ? ""
                : userMessage.toLowerCase();

        String replyText = aiReply == null
                ? ""
                : aiReply.toLowerCase();

        return "yes".equals(userText.trim())
                || "no".equals(userText.trim())
                || userText.contains("calendar")
                || userText.contains("calender")
                || userText.contains("gmail")
                || userText.contains("email")
                || userText.contains("mail")
                || replyText.contains("reply yes")
                || replyText.contains("email sent")
                || replyText.contains("calendar event");
    }
}
