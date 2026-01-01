package com.pesitwizard.fpdu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.Getter;

/**
 * Builder for constructing PI (Parameter Information) structures
 * Used when building FPDU data with parameters
 */
@Getter
public class ParameterBuilder {

    private final Parameter pi;
    private byte[] value;

    private ParameterBuilder(Parameter pi) {
        this.pi = pi;
    }

    /**
     * Create builder for a specific PI
     */
    public static ParameterBuilder forParameter(Parameter pi) {
        return new ParameterBuilder(pi);
    }

    /**
     * Set value as raw bytes
     */
    public ParameterBuilder value(byte[] value) {
        this.value = value;
        return this;
    }

    /**
     * Set value as string (for variable-length PIs)
     */
    public ParameterBuilder value(String value) {
        this.value = value.getBytes(StandardCharsets.ISO_8859_1);
        return this;
    }

    /**
     * Set value as integer (for fixed-length numeric PIs)
     */
    public ParameterBuilder value(int value) {
        if (!(pi instanceof ParameterIdentifier pi)) {
            throw new IllegalArgumentException("PI " + this.pi.getName() + " does not support integer values");
        }
        int length = pi.getLength();
        if (length == -1) {
            throw new IllegalArgumentException("Cannot use integer value for variable-length PI: " + pi.getName());
        }
        if (value < 256) {
            length = 1; // Use 1 byte for small integers
        } else if (value < 65536) {
            length = 2; // Use 2 bytes for larger integers
        } else if (value < 16777216) {
            length = 3; // Use 3 bytes for even larger integers
        } else if (value < 4294967296L) {
            length = 4; // Use 4 bytes for very large integers
        } else {
            throw new IllegalArgumentException("Integer value too large for PI: " + pi.getName());
        }
        if (length > pi.getLength()) {
            throw new IllegalArgumentException("Integer value exceeds PI length: " + pi.getName());
        }

        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[length - 1 - i] = (byte) (value >> (i * 8));
        }
        this.value = bytes;
        return this;
    }

    /**
     * Set value as long (for 8-byte PIs like file size)
     */
    public ParameterBuilder value(long value) {
        if (!(pi instanceof ParameterIdentifier pi)) {
            throw new IllegalArgumentException("PI " + this.pi.getName() + " does not support integer values");
        }

        int length = pi.getLength();
        if (length == -1) {
            throw new IllegalArgumentException("Cannot use long value for variable-length PI: " + pi.getName());
        }
        if (value < 256) {
            length = 1; // Use 1 byte for small integers
        } else if (value < 65536) {
            length = 2; // Use 2 bytes for larger integers
        } else if (value < 16777216) {
            length = 3; // Use 3 bytes for even larger integers
        } else if (value < 4294967296L) {
            length = 4; // Use 4 bytes for very large integers
        } else {
            throw new IllegalArgumentException("Integer value too large for PI: " + pi.getName());
        }
        if (length > pi.getLength()) {
            throw new IllegalArgumentException("Integer value exceeds PI length: " + pi.getName());
        }

        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[length - 1 - i] = (byte) (value >> (i * 8));
        }
        this.value = bytes;
        return this;
    }

    /**
     * Build PI structure as byte array
     * Format: [PI_ID (1 byte)] [Length (1 byte)] [Value (n bytes)]
     */
    public byte[] build() throws IOException {
        if (value == null) {
            throw new IllegalStateException("PI value not set for: " + pi.getName());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // PI identifier
        baos.write(pi.getId() & 0xff); // Ensure single byte

        // Length byte
        if (value.length < 255) {
            baos.write(value.length & 0xFF); // Length as 1 byte
        } else {
            baos.write(0xFF); // Use 255 to indicate length is larger than 255
            baos.write((value.length >> 8) & 0xFF); // Length as 2 bytes
            baos.write(value.length & 0xFF);
        }

        // Value
        baos.write(value);

        return baos.toByteArray();
    }

    /**
     * Get the PI enum this builder is for
     */
    public Parameter getPI() {
        return pi;
    }
}
