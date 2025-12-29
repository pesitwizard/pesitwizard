package com.pesitwizard.client.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pesitwizard.client.security.SecretsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST API for security/encryption configuration in the client.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/security")
@RequiredArgsConstructor
public class SecurityController {

    private final SecretsService secretsService;

    /**
     * Get current encryption status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        var status = secretsService.getStatus();
        return ResponseEntity.ok(Map.of(
                "encryption", Map.of(
                        "enabled", status.enabled(),
                        "mode", status.mode(),
                        "message", status.message())));
    }

    /**
     * Generate a new master key (for display only - user must set it as env var)
     */
    @PostMapping("/generate-key")
    public ResponseEntity<Map<String, Object>> generateKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        String base64Key = java.util.Base64.getEncoder().encodeToString(key);

        return ResponseEntity.ok(Map.of(
                "key", base64Key,
                "instructions",
                "Set this key as environment variable PESITWIZARD_SECURITY_MASTER_KEY and restart the application."));
    }

    /**
     * Test if a value can be encrypted/decrypted
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testEncryption(@RequestBody Map<String, String> request) {
        String testValue = request.getOrDefault("value", "test-encryption-value");

        try {
            if (!secretsService.isAvailable()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message",
                        "Encryption not configured. Set PESITWIZARD_SECURITY_MASTER_KEY environment variable."));
            }

            String encrypted = secretsService.encrypt(testValue);
            String decrypted = secretsService.decrypt(encrypted);
            boolean success = testValue.equals(decrypted);

            return ResponseEntity.ok(Map.of(
                    "success", success,
                    "message", success ? "Encryption working correctly" : "Decryption mismatch",
                    "encrypted", encrypted.substring(0, Math.min(20, encrypted.length())) + "..."));
        } catch (Exception e) {
            log.error("Encryption test failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Encryption test failed: " + e.getMessage()));
        }
    }

    /**
     * Test Vault connection
     */
    @PostMapping("/vault/test")
    public ResponseEntity<Map<String, Object>> testVault(@RequestBody Map<String, String> request) {
        String address = request.get("address");
        String token = request.get("token");

        if (address == null || address.isBlank() || token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Vault address and token are required"));
        }

        try {
            var httpClient = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5))
                    .build();

            var httpRequest = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(address + "/v1/sys/health"))
                    .header("X-Vault-Token", token)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            var response = httpClient.send(httpRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 429) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Vault connection successful",
                        "details", Map.of("raw", response.body())));
            } else {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Vault returned status " + response.statusCode()));
            }
        } catch (Exception e) {
            log.error("Vault test failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "message", "Connection failed: " + e.getMessage()));
        }
    }

    /**
     * Get Vault configuration instructions
     */
    @GetMapping("/vault/config")
    public ResponseEntity<Map<String, Object>> getVaultConfig() {
        return ResponseEntity.ok(Map.of(
                "instructions", "To connect to Vault, set these environment variables and restart:",
                "variables", Map.of(
                        "PESITWIZARD_SECURITY_MODE", "VAULT",
                        "PESITWIZARD_SECURITY_VAULT_ADDRESS", "http://localhost:30200",
                        "PESITWIZARD_SECURITY_VAULT_TOKEN", "<your-token>",
                        "PESITWIZARD_SECURITY_VAULT_PATH", "secret/data/pesitwizard-client"),
                "vaultAvailable", secretsService.isVaultAvailable(),
                "currentMode", secretsService.getMode()));
    }
}
