package com.debasish.whatsappassistant.service;

import com.debasish.whatsappassistant.entity.WhatsappConnection;
import com.debasish.whatsappassistant.repository.WhatsappConnectionRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class WhatsappConnectionService {

    private final WhatsappConnectionRepository repository;

    public WhatsappConnectionService(
            WhatsappConnectionRepository repository) {

        this.repository = repository;
    }

    public String generateCode(String email) {

        String code =
                "WA-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 6)
                        .toUpperCase();

        WhatsappConnection connection =
                new WhatsappConnection();

        connection.setEmail(email);
        connection.setLinkCode(code);
        connection.setIsConnected(false);

        repository.save(connection);

        return code;
    }

    public boolean connect(String linkCode, String phoneNumber) {

        return repository.findByLinkCode(linkCode.trim().toUpperCase())
                .map(connection -> {
                    connection.setPhoneNumber(phoneNumber);
                    connection.setIsConnected(true);
                    repository.save(connection);
                    return true;
                })
                .orElse(false);
    }

    public boolean isConnected(String phoneNumber) {

        return repository
                .findFirstByPhoneNumberAndIsConnectedTrueOrderByIdDesc(
                        phoneNumber
                )
                .isPresent();
    }

    public Optional<String> getConnectedEmail(String phoneNumber) {

        return repository
                .findFirstByPhoneNumberAndIsConnectedTrueOrderByIdDesc(
                        phoneNumber
                )
                .map(WhatsappConnection::getEmail);
    }
}
