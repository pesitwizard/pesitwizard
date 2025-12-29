package com.pesitwizard.server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pesitwizard.server.security.SecretsService;
import com.pesitwizard.server.service.ConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for configuring Vault encryption on the PeSIT server.
 * Called by admin to propagate Vault configuration to deployed clusters.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config/vault")
@RequiredArgsConstructor
public class VaultController {

    private final SecretsService secretsService;
    private final ConfigService configService;

    /**
     * Get current Vault status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var status = secretsService.getStatus();
        return ResponseEntity.ok(Map.of(
                "enabled", status.enabled(),
                "address", status.address() != null ? status.address() : "",
                "path", status.path() != null ? status.path() : ""));
    }

    /**
     * Configure Vault connection
     */
    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody Map<String, String> request) {
        String address = request.get("address");
        String token = request.get("token");
        String path = request.getOrDefault("path", "secret/data/pesitwizard-server");

        if (address == null || address.isBlank() || token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vault address and token are required"));
        }

        log.info("Configuring Vault from admin: {}", address);

        boolean success = secretsService.configureVault(address, token, path);

        if (success) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vault configured successfully"));
        } else {
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Failed to connect to Vault"));
        }
    }

    /**
     * Encrypt all existing partner passwords
     */
    @PostMapping("/encrypt-existing")
    public ResponseEntity<Map<String, Object>> encryptExisting() {
        if (!secretsService.isAvailable()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vault not configured"));
        }

        log.info("Encrypting existing partner passwords");

        int encrypted = 0;
        int skipped = 0;

        for (var partner : configService.getAllPartners()) {
            String password = partner.getPassword();
            if (password != null && !password.isBlank() && !secretsService.isEncrypted(password)) {
                String encryptedPwd = secretsService.encrypt(password);
                partner.setPassword(encryptedPwd);
                configService.savePartner(partner);
                encrypted++;
                log.debug("Encrypted password for partner: {}", partner.getId());
            } else {
                skipped++;
            }
        }

        log.info("Encryption complete: {} encrypted, {} skipped", encrypted, skipped);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "encrypted", encrypted,
                "skipped", skipped,
                "message", "Encrypted " + encrypted + " passwords, skipped " + skipped));
    }
}
