package com.debasish.whatsappassistant.service;

import com.debasish.whatsappassistant.entity.User;
import com.debasish.whatsappassistant.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void saveUser(String phoneNumber) {

        User user =
                userRepository.findById(phoneNumber)
                        .orElse(null);

        if (user == null) {

            user = new User();

            user.setPhoneNumber(phoneNumber);

            user.setName("Unknown");

            userRepository.save(user);

            System.out.println("User saved: " + phoneNumber);
        }
    }
}