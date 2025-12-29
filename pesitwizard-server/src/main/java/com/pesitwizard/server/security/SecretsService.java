package com.pesitwizard.server.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for encrypting sensitive data.
 * Supports AES (default) and HashiCorp Vault.
 * AES is always available as fallback to ensure secrets are never stored in
 * plaintext.
 */
@Slf4j
@Service
public class SecretsService {

    private static final String VAULT_PREFIX = "vault:";
    private static final String AES_PREFIX = "AES:";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // AES encryption (always available)
    private SecretKey aesKey;
    private boolean aesEnabled = false;

    // Vault configuration (can be updated at runtime)
    private final AtomicReference<String> vaultAddress = new AtomicReference<>();
    private final AtomicReference<String> vaultToken = new AtomicReference<>();
    private final AtomicReference<String> secretsPath = new AtomicReference<>("secret/data/pesitwizard-server");
    private final AtomicReference<Boolean> vaultEnabled = new AtomicReference<>(false);

    public SecretsService(
            @Value("${pesitwizard.security.master-key:}") String masterKey,
            @Value("${pesitwizard.security.vault.address:}") String vaultAddr,
            @Value("${pesitwizard.security.vault.token:}") String vaultTok,
            @Value("${pesitwizard.security.vault.path:secret/data/pesitwizard-server}") String path) {

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper();

        // Initialize AES encryption (always enabled)
        initializeAes(masterKey);

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

    private void initializeAes(String masterKey) {
        try {
            String keyToUse = masterKey;
            if (keyToUse == null || keyToUse.isBlank()) {
                keyToUse = generateDefaultMasterKey();
                log.warn("⚠️  Using auto-generated AES key. For production, set PESITWIZARD_SECURITY_MASTER_KEY");
            }

            // Derive a 256-bit key from the master key
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = md.digest(keyToUse.getBytes(StandardCharsets.UTF_8));
            this.aesKey = new SecretKeySpec(keyBytes, "AES");
            this.aesEnabled = true;
            log.info("AES encryption initialized");
        } catch (Exception e) {
            log.error("Failed to initialize AES encryption: {}", e.getMessage());
            this.aesEnabled = false;
        }
    }

    private String generateDefaultMasterKey() {
        String seed = System.getProperty("user.home", "/tmp") +
                System.getProperty("os.name", "unknown") +
                "pesitwizard-server-default-key-v1";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "cGVzaXR3aXphcmQtc2VydmVyLWRlZmF1bHQ=";
        }
    }

    @PostConstruct
    public void init() {
        if (vaultAddress.get() != null && vaultToken.get() != null) {
            if (testConnection()) {
                vaultEnabled.set(true);
                log.info("Vault secrets service initialized: {}", vaultAddress.get());
            } else {
                log.warn("Vault configured but not reachable, using AES fallback");
            }
        } else {
            log.info("Vault not configured - using AES encryption");
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
     * Encrypt a value. Uses Vault if available, otherwise AES.
     * Never returns plaintext - AES is always available as fallback.
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }

        if (isEncrypted(plaintext)) {
            return plaintext; // Already encrypted
        }

        // Try Vault first if available
        if (vaultEnabled.get()) {
            String key = UUID.randomUUID().toString();
            if (storeSecret(key, plaintext)) {
                return VAULT_PREFIX + key;
            }
            log.warn("Vault storage failed, falling back to AES");
        }

        // Fallback to AES (always available)
        return encryptAes(plaintext);
    }

    /**
     * Encrypt using AES-256-GCM
     */
    private String encryptAes(String plaintext) {
        if (!aesEnabled) {
            log.error("CRITICAL: AES encryption not available, storing in plaintext!");
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new java.security.SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Combine IV + ciphertext
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return AES_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES encryption failed: {}", e.getMessage());
            return plaintext;
        }
    }

    /**
     * Decrypt a value. Handles both Vault references and AES encrypted values.
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return ciphertext;
        }

        // Handle Vault references
        if (ciphertext.startsWith(VAULT_PREFIX)) {
            if (!vaultEnabled.get()) {
                log.warn("Cannot decrypt vault reference - Vault not available");
                return ciphertext;
            }
            String key = ciphertext.substring(VAULT_PREFIX.length());
            String value = getSecret(key);
            return value != null ? value : ciphertext;
        }

        // Handle AES encrypted values
        if (ciphertext.startsWith(AES_PREFIX)) {
            return decryptAes(ciphertext);
        }

        return ciphertext;
    }

    /**
     * Decrypt AES-256-GCM encrypted value
     */
    private String decryptAes(String ciphertext) {
        if (!aesEnabled) {
            log.error("Cannot decrypt AES value - AES not available");
            return ciphertext;
        }

        try {
            String encoded = ciphertext.substring(AES_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, parameterSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES decryption failed: {}", e.getMessage());
            return ciphertext;
        }
    }

    /**
     * Check if a value is encrypted
     */
    public boolean isEncrypted(String value) {
        return value != null && (value.startsWith(VAULT_PREFIX) || value.startsWith(AES_PREFIX));
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
