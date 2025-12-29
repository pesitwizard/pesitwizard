package com.pesitwizard.client.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple Vault client for storing and retrieving secrets.
 */
@Slf4j
public class VaultClient {

    private static final String PREFIX = "vault:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String vaultAddr;
    private final String vaultToken;
    private final String secretsPath;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private boolean available;

    public VaultClient(String vaultAddr, String vaultToken, String secretsPath) {
        this.vaultAddr = vaultAddr;
        this.vaultToken = vaultToken;
        this.secretsPath = secretsPath != null ? secretsPath : "secret/data/pesitwizard-client";
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();

        if (vaultAddr == null || vaultAddr.isBlank() || vaultToken == null || vaultToken.isBlank()) {
            log.debug("Vault not configured for client");
            this.available = false;
        } else {
            this.available = testConnection();
            if (this.available) {
                log.info("Vault client initialized: {}", vaultAddr);
            } else {
                log.warn("Vault configured but not reachable: {}", vaultAddr);
            }
        }
    }

    private boolean testConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(vaultAddr + "/v1/sys/health"))
                    .header("X-Vault-Token", vaultToken)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 || response.statusCode() == 429
                    || response.statusCode() == 472 || response.statusCode() == 473;
        } catch (Exception e) {
            log.warn("Vault health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Store a secret in Vault and return a reference.
     */
    public String storeSecret(String key, String value) {
        if (!available) {
            log.warn("Vault not available, cannot store secret: {}", key);
            return value;
        }

        try {
            ObjectNode dataNode = objectMapper.createObjectNode();
            ObjectNode innerData = objectMapper.createObjectNode();
            innerData.put("value", value);
            dataNode.set("data", innerData);

            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", vaultToken)
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(dataNode)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.debug("Secret stored in Vault: {}", key);
                return PREFIX + key;
            } else {
                log.error("Failed to store secret in Vault: {} - {}", key, response.body());
                return value;
            }

        } catch (Exception e) {
            log.error("Failed to store secret in Vault: {} - {}", key, e.getMessage());
            return value;
        }
    }

    /**
     * Get a secret from Vault.
     */
    public String getSecret(String key) {
        if (!available) {
            return null;
        }

        try {
            String url = vaultAddr + "/v1/" + secretsPath + "/" + key;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Vault-Token", vaultToken)
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
            } else if (response.statusCode() == 404) {
                log.debug("Secret not found in Vault: {}", key);
            } else {
                log.error("Failed to retrieve secret from Vault: {} - {}", key, response.statusCode());
            }

            return null;

        } catch (Exception e) {
            log.error("Failed to retrieve secret from Vault: {} - {}", key, e.getMessage());
            return null;
        }
    }

    /**
     * Check if a value is a Vault reference.
     */
    public boolean isVaultReference(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Resolve a Vault reference to its actual value.
     */
    public String resolveReference(String reference) {
        if (!isVaultReference(reference)) {
            return reference;
        }
        String key = reference.substring(PREFIX.length());
        String value = getSecret(key);
        return value != null ? value : reference;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getVaultAddr() {
        return vaultAddr;
    }
}
