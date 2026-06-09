package com.debasish.whatsappassistant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "whatsapp_connections")
@Getter
@Setter
public class WhatsappConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String phoneNumber;

    private String linkCode;

    private Boolean isConnected;
}