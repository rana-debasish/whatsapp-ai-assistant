package com.debasish.whatsappassistant.controller;

import com.debasish.whatsappassistant.service.ComposioService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Map;

@Controller
@RequestMapping("/api/composio")
public class ComposioController {

    private final ComposioService composioService;

    public ComposioController(
            ComposioService composioService) {

        this.composioService = composioService;
    }

    @PostMapping("/{toolkit}/connect")
    @ResponseBody
    public Map<String, Object> connect(
            @PathVariable String toolkit,
            @RequestParam String email) {

        String callbackUrl =
                ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/api/composio/callback")
                        .queryParam("email", email)
                        .queryParam("toolkit", toolkit)
                        .build()
                        .toUriString();

        return composioService.createConnectLink(
                email,
                toolkit,
                callbackUrl
        );
    }

    @GetMapping("/{toolkit}/status")
    @ResponseBody
    public Map<String, Object> status(
            @PathVariable String toolkit,
            @RequestParam String email) {

        return composioService.getStatus(email, toolkit);
    }

    @PostMapping("/{toolkit}/disconnect")
    @ResponseBody
    public Map<String, Object> disconnect(
            @PathVariable String toolkit,
            @RequestParam String email) {

        return composioService.disconnect(email, toolkit);
    }

    @GetMapping("/callback")
    public RedirectView callback(
            @RequestParam String email,
            @RequestParam String toolkit,
            @RequestParam(required = false) String status,
            @RequestParam(name = "connected_account_id",
                    required = false)
            String connectedAccountId,
            @RequestParam(name = "connectedAccountId",
                    required = false)
            String camelCaseConnectedAccountId) {

        String accountId = connectedAccountId != null
                ? connectedAccountId
                : camelCaseConnectedAccountId;

        if ("success".equalsIgnoreCase(status)
                && accountId != null
                && !accountId.isBlank()) {

            composioService.markConnectedFromCallback(
                    email,
                    toolkit,
                    accountId
            );
        }

        return new RedirectView("/dashboard");
    }
}
