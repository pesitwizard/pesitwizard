package com.pesitwizard.server.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for encrypting sensitive data using HashiCorp Vault.
 * Supports runtime configuration via API.
 */
@Slf4j
@Service
public class SecretsService {

    private static final String VAULT_PREFIX = "vault:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Vault configuration (can be updated at runtime)
    private final AtomicReference<String> vaultAddress = new AtomicReference<>();
    private final AtomicReference<String> vaultToken = new AtomicReference<>();
    private final AtomicReference<String> secretsPath = new AtomicReference<>("secret/data/pesitwizard-server");
    private final AtomicReference<Boolean> vaultEnabled = new AtomicReference<>(false);

    public SecretsService(
            @Value("${pesitwizard.security.vault.address:}") String vaultAddr,
            @Value("${pesitwizard.security.vault.token:}") String vaultTok,
            @Value("${pesitwizard.security.vault.path:secret/data/pesitwizard-server}") String path) {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();

        if (vaultAddr != null && !vaultAddr.isBlank()) {
            this.vaultAddress.set(vaultAddr);
        }
        if (vaultTok != null && !vaultTok.isBlank()) {
            this.vaultToken.set(vaultTok);
        }
        if (path != null && !path.isBlank()) {
            this.secretsPath.set(path);
        }
    }

    @PostConstruct
    public void init() {
        if (vaultAddress.get() != null && vaultToken.get() != null) {
            if (testConnection()) {
                vaultEnabled.set(true);
                log.info("Vault secrets service initialized: {}", vaultAddress.get());
            } else {
                log.warn("Vault configured but not reachable: {}", vaultAddress.get());
            }
        } else {
            log.info("Vault not configured - secrets will be stored in plaintext");
        }
    }

    /**
     * Configure Vault at runtime (called from admin via API)
     */
    public boolean configureVault(String address, String token, String path) {
        log.info("Configuring Vault: {}", address);

        String oldAddress = vaultAddress.get();
        String oldToken = vaultToken.get();
        String oldPath = secretsPath.get();

        vaultAddress.set(address);
        vaultToken.set(token);
        if (path != null && !path.isBlank()) {
            secretsPath.set(path);
        }

        if (testConnection()) {
            vaultEnabled.set(true);
            log.info("Vault configured successfully");
            return true;
        } else {
            // Rollback
            vaultAddress.set(oldAddress);
            vaultToken.set(oldToken);
            secretsPath.set(oldPath);
            log.error("Vault configuration failed - connection test failed");
            return false;
        }
    }

    /**
     * Encrypt a value (store in Vault and return reference)
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        if (isEncrypted(plaintext)) {
            return plaintext; // Already encrypted
        }

        if (!vaultEnabled.get()) {
            return plaintext; // Vault not available
        }

        String key = UUID.randomUUID().toString();
        if (storeSecret(key, plaintext)) {
            return VAULT_PREFIX + key;
        }
        return plaintext; // Fallback to plaintext on error
    }

    /**
     * Decrypt a value (fetch from Vault if reference)
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VAULT_PREFIX)) {
            return ciphertext;
        }

        if (!vaultEnabled.get()) {
            log.warn("Cannot decrypt vault reference - Vault not available");
            return ciphertext;
        }

        String key = ciphertext.substring(VAULT_PREFIX.length());
        String value = getSecret(key);
        return value != null ? value : ciphertext;
    }

    /**
     * Check if a value is encrypted
     */
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(VAULT_PREFIX);
    }

    /**
     * Check if Vault is available
     */
    public boolean isAvailable() {
        return vaultEnabled.get();
    }

    /**
     * Get current status
     */
    public VaultStatus getStatus() {
        return new VaultStatus(
                vaultEnabled.get(),
                vaultAddress.get(),
                secretsPath.get());
    }

    private boolean testConnection() {
        try {
            String addr = vaultAddress.get();
            String tok = vaultToken.get();
            if (addr == null || tok == null)
                return false;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(addr + "/v1/sys/health"))
                    .header("X-Vault-Token", tok)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 429;
        } catch (Exception e) {
            log.warn("Vault connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean storeSecret(String key, String value) {
        try {
            ObjectNode dataNode = objectMapper.createObjectNode();
            ObjectNode innerData = objectMapper.createObjectNode();
            innerData.put("value", value);
            dataNode.set("data", innerData);

            String url = vaultAddress.get() + "/v1/" + secretsPath.get() + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", vaultToken.get())
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(dataNode)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Secret stored in Vault: {}", key);
                return true;
            } else {
                log.error("Failed to store secret: {} - {}", key, response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to store secret: {} - {}", key, e.getMessage());
            return false;
        }
    }

    private String getSecret(String key) {
        try {
            String url = vaultAddress.get() + "/v1/" + secretsPath.get() + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", vaultToken.get())
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.path("data").path("data").path("value");
                if (!data.isMissingNode()) {
                    return data.asText();
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Failed to get secret: {} - {}", key, e.getMessage());
            return null;
        }
    }

    public record VaultStatus(boolean enabled, String address, String path) {
    }
}
