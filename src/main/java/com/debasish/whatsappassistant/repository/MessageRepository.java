package com.debasish.whatsappassistant.repository;

import com.debasish.whatsappassistant.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long> {
    List<Message> findTop5ByPhoneNumberOrderByIdDesc(
            String phoneNumber
    );
}