package com.debasish.whatsappassistant.controller;

import com.debasish.whatsappassistant.service.WhatsappConnectionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappConnectionController {

    private final WhatsappConnectionService service;

    public WhatsappConnectionController(
            WhatsappConnectionService service) {

        this.service = service;
    }

    @PostMapping("/connect")
    public String connect(
            @RequestParam String email) {

        return service.generateCode(email);
    }
}