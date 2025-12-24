package com.pesitwizard.server.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FileSystemController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "pesit.server.filesystem.base-path=${java.io.tmpdir}/pesitwizard-test")
@DisplayName("FileSystemController Tests")
class FileSystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static String basePath;

    @BeforeAll
    static void setup() throws Exception {
        basePath = System.getProperty("java.io.tmpdir") + "/pesitwizard-test";
        Files.createDirectories(Path.of(basePath));
    }

    @Test
    @DisplayName("browse should reject paths outside base path")
    void browseShouldRejectPathsOutsideBasePath() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/browse?path=/etc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("browse should reject path traversal attempts")
    void browseShouldRejectPathTraversal() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/browse?path=" + basePath + "/../etc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("browse should return base path contents when no path specified")
    void browseShouldReturnBasePathContents() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/browse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePath").value(basePath))
                .andExpect(jsonPath("$.currentPath").value(basePath));
    }

    @Test
    @DisplayName("browse should list directory contents")
    void browseShouldListDirectoryContents() throws Exception {
        // Create a test file in the base path
        Path testFile = Path.of(basePath, "testfile.txt");
        Files.writeString(testFile, "test content");

        mockMvc.perform(get("/api/v1/filesystem/browse?path=" + basePath))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray());

        // Clean up
        Files.deleteIfExists(testFile);
    }

    @Test
    @DisplayName("mkdir should reject paths outside base path")
    void mkdirShouldRejectPathsOutsideBasePath() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/mkdir?path=/tmp/evil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("mkdir should reject path traversal attempts")
    void mkdirShouldRejectPathTraversal() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/mkdir?path=" + basePath + "/../tmp/evil"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("mkdir should create directory under base path")
    void mkdirShouldCreateDirectoryUnderBasePath() throws Exception {
        String newDir = basePath + "/newdir_" + System.currentTimeMillis();

        mockMvc.perform(get("/api/v1/filesystem/mkdir?path=" + newDir))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        // Clean up
        Files.deleteIfExists(Path.of(newDir));
    }

    @Test
    @DisplayName("mkdir should fail if path already exists")
    void mkdirShouldFailIfPathExists() throws Exception {
        mockMvc.perform(get("/api/v1/filesystem/mkdir?path=" + basePath))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Path already exists: " + basePath));
    }
}
