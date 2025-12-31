package com.pesitwizard.server.model;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("TransferContext Tests")
class TransferContextTest {

    private TransferContext context;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        context = new TransferContext();
    }

    @Test
    @DisplayName("should append data and track bytes")
    void shouldAppendDataAndTrackBytes() throws IOException {
        byte[] data1 = "Hello ".getBytes();
        byte[] data2 = "World!".getBytes();

        // Setup streaming to temp file
        context.setLocalPath(tempDir.resolve("test.dat"));
        context.openOutputStream();

        context.appendData(data1);
        assertEquals(data1.length, context.getBytesTransferred());
        assertEquals(1, context.getRecordsTransferred());

        context.appendData(data2);
        assertEquals(data1.length + data2.length, context.getBytesTransferred());
        assertEquals(2, context.getRecordsTransferred());

        context.closeOutputStream();
    }

    @Test
    @DisplayName("should get accumulated data")
    void shouldGetAccumulatedData() throws IOException {
        // Setup streaming to temp file
        context.setLocalPath(tempDir.resolve("test2.dat"));
        context.openOutputStream();

        context.appendData("Hello ".getBytes());
        context.appendData("World!".getBytes());
        context.closeOutputStream();

        byte[] result = context.getData();
        assertEquals("Hello World!", new String(result));
    }

    @Test
    @DisplayName("should reset all fields")
    void shouldResetAllFields() throws IOException {
        // Set some values
        context.setTransferId(123);
        context.setFilename("test.txt");
        context.setLocalPath(tempDir.resolve("test3.dat"));
        context.setPriority(5);
        context.setWriteMode(true);
        context.setRestart(true);
        context.setStartTime(Instant.now());
        context.openOutputStream();
        context.appendData("test data".getBytes());

        // Reset
        context.reset();

        // Verify all fields are reset
        assertEquals(0, context.getTransferId());
        assertNull(context.getFilename());
        assertNull(context.getLocalPath());
        assertEquals(0, context.getPriority());
        assertFalse(context.isWriteMode());
        assertFalse(context.isRestart());
        assertNull(context.getStartTime());
        assertEquals(0, context.getBytesTransferred());
        assertEquals(0, context.getRecordsTransferred());
    }

    @Test
    @DisplayName("should store transfer attributes")
    void shouldStoreTransferAttributes() {
        context.setTransferId(42);
        context.setFileType(1);
        context.setFilename("DATA.DAT");
        context.setLocalPath(Path.of("/data/received/DATA.DAT"));
        context.setPriority(3);
        context.setDataCode(2); // BINARY
        context.setRecordFormat(1); // Variable
        context.setRecordLength(1024);
        context.setFileOrganization(0);
        context.setMaxEntitySize(32768);
        context.setCompression(0);
        context.setWriteMode(true);
        context.setRestart(false);
        context.setRestartPoint(0);
        context.setCurrentSyncPoint(5);
        context.setStartTime(Instant.now());
        context.setClientId("CLIENT1");
        context.setBankId("BANK1");

        assertEquals(42, context.getTransferId());
        assertEquals(1, context.getFileType());
        assertEquals("DATA.DAT", context.getFilename());
        assertEquals(Path.of("/data/received/DATA.DAT"), context.getLocalPath());
        assertEquals(3, context.getPriority());
        assertEquals(2, context.getDataCode());
        assertEquals(1, context.getRecordFormat());
        assertEquals(1024, context.getRecordLength());
        assertEquals(0, context.getFileOrganization());
        assertEquals(32768, context.getMaxEntitySize());
        assertEquals(0, context.getCompression());
        assertTrue(context.isWriteMode());
        assertFalse(context.isRestart());
        assertEquals(0, context.getRestartPoint());
        assertEquals(5, context.getCurrentSyncPoint());
        assertNotNull(context.getStartTime());
        assertEquals("CLIENT1", context.getClientId());
        assertEquals("BANK1", context.getBankId());
    }

    @Test
    @DisplayName("should handle empty data append")
    void shouldHandleEmptyDataAppend() throws IOException {
        context.setLocalPath(tempDir.resolve("empty.dat"));
        context.openOutputStream();
        context.appendData(new byte[0]);
        assertEquals(0, context.getBytesTransferred());
        assertEquals(1, context.getRecordsTransferred()); // Still counts as a record
        context.closeOutputStream();
    }

    @Test
    @DisplayName("should track end time")
    void shouldTrackEndTime() {
        assertNull(context.getEndTime());

        Instant endTime = Instant.now();
        context.setEndTime(endTime);

        assertEquals(endTime, context.getEndTime());
    }
}
