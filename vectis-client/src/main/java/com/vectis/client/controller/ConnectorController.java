package com.vectis.client.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vectis.client.connector.ConnectorRegistry;
import com.vectis.client.entity.StorageConnection;
import com.vectis.client.repository.StorageConnectionRepository;
import com.vectis.connector.ConfigParameter;
import com.vectis.connector.ConnectorException;
import com.vectis.connector.ConnectorFactory;
import com.vectis.connector.StorageConnector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/connectors")
@RequiredArgsConstructor
@Slf4j
public class ConnectorController {

    private final ConnectorRegistry connectorRegistry;
    private final StorageConnectionRepository connectionRepository;
    private final ObjectMapper objectMapper;

    // ========== Connector Types ==========

    @GetMapping("/types")
    public ResponseEntity<List<ConnectorTypeDto>> listConnectorTypes() {
        List<ConnectorTypeDto> types = connectorRegistry.getAvailableTypes().stream()
                .map(type -> {
                    ConnectorFactory factory = connectorRegistry.getFactory(type);
                    return new ConnectorTypeDto(
                            factory.getType(), factory.getName(), factory.getVersion(),
                            factory.getDescription(), factory.getRequiredParameters(),
                            factory.getOptionalParameters());
                }).toList();
        return ResponseEntity.ok(types);
    }

    @GetMapping("/types/{type}")
    public ResponseEntity<ConnectorTypeDto> getConnectorType(@PathVariable String type) {
        ConnectorFactory factory = connectorRegistry.getFactory(type);
        if (factory == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(new ConnectorTypeDto(
                factory.getType(), factory.getName(), factory.getVersion(),
                factory.getDescription(), factory.getRequiredParameters(),
                factory.getOptionalParameters()));
    }

    @PostMapping("/types/reload")
    public ResponseEntity<Map<String, Object>> reloadConnectors() {
        connectorRegistry.reloadConnectors();
        return ResponseEntity
                .ok(Map.of("message", "Connectors reloaded", "types", connectorRegistry.getAvailableTypes()));
    }

    @PostMapping("/types/import")
    public ResponseEntity<Map<String, Object>> importConnector(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided"));
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".jar")) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must be a JAR"));
        }
        try {
            java.nio.file.Path connectorsDir = java.nio.file.Paths.get("connectors");
            java.nio.file.Files.createDirectories(connectorsDir);
            file.transferTo(connectorsDir.resolve(filename).toFile());
            connectorRegistry.reloadConnectors();
            return ResponseEntity.ok(Map.of("message", "Connector imported", "filename", filename, "types",
                    connectorRegistry.getAvailableTypes()));
        } catch (Exception e) {
            log.error("Failed to import connector", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== Connection Instances ==========

    @GetMapping("/connections")
    public ResponseEntity<List<StorageConnection>> listConnections() {
        return ResponseEntity.ok(connectionRepository.findAll());
    }

    @GetMapping("/connections/{id}")
    public ResponseEntity<StorageConnection> getConnection(@PathVariable String id) {
        return connectionRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/connections")
    public ResponseEntity<?> createConnection(@RequestBody ConnectionRequest request) {
        if (connectionRepository.existsByName(request.name())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Connection name already exists"));
        }
        if (connectorRegistry.getFactory(request.connectorType()) == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unknown connector type: " + request.connectorType()));
        }
        try {
            StorageConnection connection = StorageConnection.builder()
                    .name(request.name()).description(request.description())
                    .connectorType(request.connectorType())
                    .configJson(objectMapper.writeValueAsString(request.config()))
                    .enabled(request.enabled() != null ? request.enabled() : true).build();
            return ResponseEntity.ok(connectionRepository.save(connection));
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid configuration"));
        }
    }

    @PutMapping("/connections/{id}")
    public ResponseEntity<?> updateConnection(@PathVariable String id, @RequestBody ConnectionRequest request) {
        return connectionRepository.findById(id).map(conn -> {
            try {
                conn.setName(request.name());
                conn.setDescription(request.description());
                conn.setConnectorType(request.connectorType());
                conn.setConfigJson(objectMapper.writeValueAsString(request.config()));
                if (request.enabled() != null)
                    conn.setEnabled(request.enabled());
                return ResponseEntity.ok(connectionRepository.save(conn));
            } catch (JsonProcessingException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid configuration"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> deleteConnection(@PathVariable String id) {
        if (!connectionRepository.existsById(id))
            return ResponseEntity.notFound().build();
        connectionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/connections/{id}/test")
    public ResponseEntity<?> testConnection(@PathVariable String id) {
        return connectionRepository.findById(id).map(conn -> {
            try {
                Map<String, String> config = objectMapper.readValue(conn.getConfigJson(), new TypeReference<>() {
                });
                StorageConnector connector = connectorRegistry.createConnector(conn.getConnectorType(), config);
                boolean success = connector.testConnection();
                connector.close();
                conn.setLastTestedAt(Instant.now());
                conn.setLastTestSuccess(success);
                conn.setLastTestError(null);
                connectionRepository.save(conn);
                return ResponseEntity.ok(
                        Map.of("success", success, "message", success ? "Connection successful" : "Connection failed"));
            } catch (ConnectorException e) {
                conn.setLastTestedAt(Instant.now());
                conn.setLastTestSuccess(false);
                conn.setLastTestError(e.getMessage());
                connectionRepository.save(conn);
                return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/connections/{id}/browse")
    public ResponseEntity<?> browseConnection(@PathVariable String id, @RequestParam(defaultValue = ".") String path) {
        return connectionRepository.findById(id).map(conn -> {
            try {
                Map<String, String> config = objectMapper.readValue(conn.getConfigJson(), new TypeReference<>() {
                });
                StorageConnector connector = connectorRegistry.createConnector(conn.getConnectorType(), config);
                var files = connector.list(path);
                connector.close();
                return ResponseEntity.ok(files);
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/local/browse")
    public ResponseEntity<?> browseLocal(@RequestParam(defaultValue = ".") String path) {
        try {
            // Use the local connector with default base path
            StorageConnector connector = connectorRegistry.createConnector("local", Map.of("basePath", "/"));
            var files = connector.list(path);
            connector.close();
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== DTOs ==========

    public record ConnectorTypeDto(String type, String name, String version, String description,
            List<ConfigParameter> requiredParameters, List<ConfigParameter> optionalParameters) {
    }

    public record ConnectionRequest(String name, String description, String connectorType,
            Map<String, String> config, Boolean enabled) {
    }
}
