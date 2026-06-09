package com.debasish.whatsappassistant.repository;

import com.debasish.whatsappassistant.entity.ComposioConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComposioConnectionRepository
        extends JpaRepository<ComposioConnection, Long> {

    Optional<ComposioConnection> findFirstByEmailAndToolkitOrderByIdDesc(
            String email,
            String toolkit
    );

    Optional<ComposioConnection> findFirstByEmailAndToolkitAndStatusOrderByIdDesc(
            String email,
            String toolkit,
            String status
    );
}
