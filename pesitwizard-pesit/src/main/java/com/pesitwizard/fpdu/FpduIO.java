package com.pesitwizard.fpdu;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level FPDU I/O operations for reading and writing FPDUs over streams.
 * Used by both client (PesitSession) and server (TcpConnectionHandler).
 */
@Slf4j
public class FpduIO {

    /**
     * Read a single FPDU from the input stream with EBCDIC-aware length handling.
     * IBM CX sends PURE EBCDIC where even the length prefix is EBCDIC-encoded.
     * This method detects EBCDIC length prefix and handles it correctly.
     *
     * @param in DataInputStream to read from
     * @return Raw FPDU bytes (without the length prefix, still in original encoding)
     * @throws IOException if read fails or connection closed
     */
    public static byte[] readRawFpduWithEbcdicDetection(DataInputStream in) throws IOException {
        // Read first 2 bytes (length prefix)
        byte[] lengthBytes = new byte[2];
        in.readFully(lengthBytes);

        // Check if length bytes are EBCDIC (both bytes >= 0x80 suggests EBCDIC)
        boolean lengthIsEbcdic = (lengthBytes[0] & 0xFF) >= 0x80 && (lengthBytes[1] & 0xFF) >= 0x80;

        int length;
        if (lengthIsEbcdic) {
            // Convert EBCDIC length bytes to ASCII, then interpret as binary
            byte[] asciiLengthBytes = EbcdicConverter.ebcdicToAscii(lengthBytes);
            length = ((asciiLengthBytes[0] & 0xFF) << 8) | (asciiLengthBytes[1] & 0xFF);
            log.debug("Detected EBCDIC length prefix: {:02X} {:02X} (EBCDIC) -> {:02X} {:02X} (ASCII) -> {} bytes",
                lengthBytes[0] & 0xFF, lengthBytes[1] & 0xFF,
                asciiLengthBytes[0] & 0xFF, asciiLengthBytes[1] & 0xFF, length);
        } else {
            // Standard binary length prefix
            length = ((lengthBytes[0] & 0xFF) << 8) | (lengthBytes[1] & 0xFF);
        }

        if (length <= 0 || length > 65535) {
            throw new IOException("Invalid FPDU length: " + length);
        }

        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    /**
     * Read a single FPDU from the input stream.
     * Reads the 2-byte length prefix, then the FPDU data.
     *
     * @param in DataInputStream to read from
     * @return Raw FPDU bytes (without the length prefix)
     * @throws IOException if read fails or connection closed
     */
    public static byte[] readRawFpdu(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length <= 0) {
            throw new IOException("Invalid FPDU length: " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return data;
    }

    /**
     * Read and parse a single FPDU from the input stream.
     * 
     * @param in DataInputStream to read from
     * @return Parsed Fpdu object
     * @throws IOException if read or parse fails
     */
    public static Fpdu readFpdu(DataInputStream in) throws IOException {
        byte[] rawData = readRawFpdu(in);
        FpduParser parser = new FpduParser(rawData);
        return parser.parse();
    }

    /**
     * Write an FPDU to the output stream.
     * Writes the 2-byte length prefix followed by the FPDU data.
     * 
     * @param out  DataOutputStream to write to
     * @param fpdu Fpdu to send
     * @throws IOException if write fails
     */
    public static void writeFpdu(DataOutputStream out, Fpdu fpdu) throws IOException {
        byte[] data = FpduBuilder.buildFpdu(fpdu);
        out.writeShort(data.length);
        out.write(data);
        out.flush();
    }

    /**
     * Write an FPDU with data payload (for DTF) to the output stream.
     * 
     * @param out      DataOutputStream to write to
     * @param fpduType FPDU type
     * @param idDst    Destination connection ID
     * @param idSrc    Source connection ID
     * @param payload  Data payload
     * @throws IOException if write fails
     */
    public static void writeFpduWithData(DataOutputStream out, FpduType fpduType,
            int idDst, int idSrc, byte[] payload) throws IOException {
        byte[] data = FpduBuilder.buildFpdu(fpduType, idDst, idSrc, payload);
        out.writeShort(data.length);
        out.write(data);
        out.flush();
    }

    /**
     * Write raw FPDU bytes to the output stream.
     * 
     * @param out     DataOutputStream to write to
     * @param rawData Raw FPDU bytes (without length prefix)
     * @throws IOException if write fails
     */
    public static void writeRawFpdu(DataOutputStream out, byte[] rawData) throws IOException {
        out.writeShort(rawData.length);
        out.write(rawData);
        out.flush();
    }

    /**
     * Check if raw FPDU bytes represent a DTF (data transfer) FPDU.
     * DTF FPDUs have phase=0x00 and type=0x00, 0x40, 0x41, or 0x42.
     * 
     * @param rawData Raw FPDU bytes
     * @return true if this is a DTF FPDU
     */
    public static boolean isDtf(byte[] rawData) {
        if (rawData == null || rawData.length < 4) {
            return false;
        }
        int phase = rawData[2] & 0xFF;
        int type = rawData[3] & 0xFF;
        return phase == 0x00 && (type == 0x00 || type == 0x40 || type == 0x41 || type == 0x42);
    }

    /**
     * Check if raw FPDU bytes represent a DTF.END FPDU.
     * DTF.END has phase=0xC0 and type=0x22.
     * 
     * @param rawData Raw FPDU bytes
     * @return true if this is a DTF.END FPDU
     */
    public static boolean isDtfEnd(byte[] rawData) {
        if (rawData == null || rawData.length < 4) {
            return false;
        }
        int phase = rawData[2] & 0xFF;
        int type = rawData[3] & 0xFF;
        return phase == 0xC0 && type == 0x22;
    }

    /**
     * Extract data payload from a DTF FPDU.
     * DTF header is 6 bytes: len(2) + phase(1) + type(1) + idDst(1) + idSrc(1).
     * 
     * @param rawData Raw DTF FPDU bytes (with length header)
     * @return Data payload, or empty array if no data
     */
    public static byte[] extractDtfData(byte[] rawData) {
        if (rawData == null || rawData.length <= 6) {
            return new byte[0];
        }
        byte[] data = new byte[rawData.length - 6];
        System.arraycopy(rawData, 6, data, 0, data.length);
        return data;
    }

    /**
     * Get the phase and type bytes from raw FPDU data.
     * 
     * @param rawData Raw FPDU bytes
     * @return int array [phase, type] or null if invalid
     */
    public static int[] getPhaseAndType(byte[] rawData) {
        if (rawData == null || rawData.length < 4) {
            return null;
        }
        return new int[] { rawData[2] & 0xFF, rawData[3] & 0xFF };
    }
}
