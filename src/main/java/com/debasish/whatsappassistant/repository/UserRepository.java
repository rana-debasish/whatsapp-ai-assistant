package com.debasish.whatsappassistant.repository;

import com.debasish.whatsappassistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository
        extends JpaRepository<User, String> {

}