package com.pesitwizard.server.handler;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSocket;

import com.pesitwizard.fpdu.EbcdicConverter;
import com.pesitwizard.fpdu.Fpdu;
import com.pesitwizard.fpdu.FpduBuilder;
import com.pesitwizard.fpdu.FpduIO;
import com.pesitwizard.fpdu.FpduParser;
import com.pesitwizard.server.config.PesitServerProperties;
import com.pesitwizard.server.model.SessionContext;
import com.pesitwizard.server.state.ServerState;

import lombok.extern.slf4j.Slf4j;

/**
 * Handles a single TCP connection for PeSIT protocol
 */
@Slf4j
public class TcpConnectionHandler implements Runnable {

    private final Socket socket;
    private final PesitSessionHandler sessionHandler;
    private final PesitServerProperties properties;
    private final String serverId;
    private SessionContext sessionContext;

    public TcpConnectionHandler(Socket socket, PesitSessionHandler sessionHandler,
            PesitServerProperties properties, String serverId) {
        this.socket = socket;
        this.sessionHandler = sessionHandler;
        this.properties = properties;
        this.serverId = serverId;
    }

    @Override
    public void run() {
        String remoteAddress = socket.getRemoteSocketAddress().toString();
        log.info("New connection from {}", remoteAddress);

        try {
            socket.setSoTimeout(properties.getReadTimeout());
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);

            // For SSL sockets, explicitly complete the handshake before reading data
            if (socket instanceof SSLSocket sslSocket) {
                try {
                    sslSocket.startHandshake();
                    log.info("TLS handshake completed: protocol={}, cipher={}",
                            sslSocket.getSession().getProtocol(),
                            sslSocket.getSession().getCipherSuite());
                } catch (IOException e) {
                    log.error("TLS handshake failed: {}", e.getMessage());
                    throw e;
                }
            }

            sessionContext = sessionHandler.createSession(remoteAddress, serverId);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Main protocol loop - continue until session ends or socket closes
            boolean sessionActive = true;
            while (!socket.isClosed() && sessionActive) {

                try {
                    // Read raw data from network
                    byte[] rawData = FpduIO.readRawFpdu(in);

                    // DEBUG: Log raw bytes
                    if (rawData.length >= 8) {
                        log.debug("[{}] Raw FPDU bytes [0-7]: {} {} {} {} {} {} {} {}",
                            sessionContext.getSessionId(),
                            String.format("%02X", rawData[0] & 0xFF),
                            String.format("%02X", rawData[1] & 0xFF),
                            String.format("%02X", rawData[2] & 0xFF),
                            String.format("%02X", rawData[3] & 0xFF),
                            String.format("%02X", rawData[4] & 0xFF),
                            String.format("%02X", rawData[5] & 0xFF),
                            String.format("%02X", rawData[6] & 0xFF),
                            String.format("%02X", rawData[7] & 0xFF));
                    }

                    // Handle pre-connection handshake (IBM CX compatibility)
                    // This is a 24-byte PURE EBCDIC message that comes BEFORE the CONNECT FPDU
                    if (!sessionContext.isPreConnectionHandled() && rawData.length == 24) {
                        // Check if it's EBCDIC (pre-connection message)
                        boolean isEbcdic = EbcdicConverter.isEbcdic(rawData);
                        if (isEbcdic) {
                            byte[] asciiData = EbcdicConverter.toAscii(rawData);
                            String preConnMsg = new String(asciiData).trim();
                            if (preConnMsg.startsWith("PESIT")) {
                                sessionContext.setEbcdicEncoding(true);
                                log.info("[{}] Client uses EBCDIC encoding (IBM mainframe)", sessionContext.getSessionId());
                                handlePreConnection(asciiData, out);
                                sessionContext.setPreConnectionHandled(true);
                                continue; // Read next message (the actual CONNECT FPDU)
                            }
                        }
                    }

                    // Parse FPDU - header is binary, but string parameters may be in EBCDIC
                    FpduParser parser = new FpduParser(rawData, sessionContext.isEbcdicEncoding());
                    Fpdu fpdu = parser.parse();

                    log.debug("[{}] Received FPDU: type={} (encoding: {})",
                        sessionContext.getSessionId(), fpdu.getFpduType(),
                        sessionContext.isEbcdicEncoding() ? "EBCDIC" : "ASCII");

                    // Convert back to bytes for existing handler (TODO: refactor handler to work with Fpdu objects)
                    byte[] fpduData = FpduBuilder.buildFpdu(fpdu);

                    // Process the FPDU (may stream data directly to output for READ)
                    byte[] response = null;
                    try {
                        response = sessionHandler.processIncomingFpdu(sessionContext, fpduData, out);
                    } catch (Exception e) {
                        log.error("[{}] Error processing FPDU: {}", sessionContext.getSessionId(), e.getMessage(), e);
                        continue;
                    }

                    // Send response if any (READ streams directly, so response may be null)
                    if (response != null) {
                        // Convert response to client encoding (EBCDIC if needed)
                        byte[] encodedResponse = EbcdicConverter.toClientEncoding(
                            response, sessionContext.isEbcdicEncoding());

                        FpduIO.writeRawFpdu(out, encodedResponse);
                        log.debug("[{}] Sent {} bytes (encoding: {})",
                            sessionContext.getSessionId(), encodedResponse.length,
                            sessionContext.isEbcdicEncoding() ? "EBCDIC" : "ASCII");
                    }

                    // Check if session ended normally (RELCONF sent or ABORT)
                    if (sessionContext.getState() == ServerState.CN01_REPOS || sessionContext.isAborted()) {
                        log.info("[{}] Session ended normally", sessionContext.getSessionId());
                        sessionActive = false;
                    }

                } catch (SocketTimeoutException e) {
                    log.warn("[{}] Read timeout", sessionContext.getSessionId());
                    break;
                } catch (EOFException e) {
                    log.info("[{}] Client disconnected", sessionContext.getSessionId());
                    break;
                }
            }

        } catch (SocketException e) {
            log.info("[{}] Connection reset: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage());
        } catch (IOException e) {
            log.error("[{}] IO error: {}",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown",
                    e.getMessage(), e);
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
            log.info("[{}] Connection closed",
                    sessionContext != null ? sessionContext.getSessionId() : "unknown");
        } catch (IOException e) {
            log.warn("Error closing socket: {}", e.getMessage());
        }
    }

    /**
     * Handle pre-connection handshake (IBM CX compatibility)
     *
     * Message format (24 bytes, EBCDIC encoded):
     * - 8 bytes: Protocol identifier ("PESIT   ")
     * - 8 bytes: Client identifier (e.g., "CXCLIENT")
     * - 8 bytes: Password (e.g., "TEST123 ")
     *
     * Response: "ACK0" (4 bytes in EBCDIC)
     */
    private void handlePreConnection(byte[] asciiData, DataOutputStream out) throws IOException {
        // Parse the 24-byte message
        String protocol = new String(asciiData, 0, 8).trim();
        String identifier = new String(asciiData, 8, 8).trim();
        String password = new String(asciiData, 16, 8).trim();

        log.info("[{}] Pre-connection handshake: protocol={}, identifier={}, password={}",
            sessionContext.getSessionId(), protocol, identifier, password.replaceAll(".", "*"));

        // Send ACK0 response in EBCDIC (without frame length prefix)
        byte[] ack = "ACK0".getBytes();
        byte[] ebcdicAck = EbcdicConverter.toClientEncoding(ack, true);

        // Write ACK0 with frame length prefix
        out.writeShort(4); // Frame length
        out.write(ebcdicAck);
        out.flush();

        log.info("[{}] Sent pre-connection ACK0", sessionContext.getSessionId());
    }

    /**
     * Get the session context for this connection
     */
    public SessionContext getSessionContext() {
        return sessionContext;
    }
}
