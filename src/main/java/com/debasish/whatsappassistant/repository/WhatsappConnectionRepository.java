package com.debasish.whatsappassistant.repository;

import com.debasish.whatsappassistant.entity.WhatsappConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WhatsappConnectionRepository
        extends JpaRepository<WhatsappConnection, Long> {

    Optional<WhatsappConnection> findByLinkCode(String linkCode);

    Optional<WhatsappConnection> findFirstByPhoneNumberAndIsConnectedTrueOrderByIdDesc(
            String phoneNumber
    );
}
