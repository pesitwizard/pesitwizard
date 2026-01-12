package com.pesitwizard.fpdu;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for a single PeSIT FPDU.
 * FPDU structure: [size (2 bytes, binary)][phase][type][idDst][idSrc][params or data]
 *
 * Note on IBM CX encoding:
 * - Pre-connection message (24 bytes): PURE EBCDIC
 * - FPDU messages: ALL fields are in ASCII/binary (no EBCDIC conversion needed)
 *
 * Note: The global frame length has been consumed by FpduIO.readRawFpdu(),
 * but each FPDU inside the frame has its own 2-byte length prefix.
 */
@Slf4j
public class FpduParser {
    private final ByteBuffer buffer;
    private final int fpduLength;

    public FpduParser(byte[] data) {
        this.buffer = ByteBuffer.wrap(data);
        // Read FPDU length from header (first 2 bytes of the FPDU itself, always binary)
        this.fpduLength = buffer.getShort() & 0xFFFF;
        if (fpduLength != data.length) {
            log.warn("FPDU length mismatch: FPDU header says {}, actual data is {} bytes", fpduLength, data.length);
        }
    }

    @Deprecated
    public FpduParser(byte[] data, boolean ebcdicEncoding) {
        this(data); // Ignore ebcdicEncoding flag - FPDU parameters are ASCII
    }

    public Fpdu parse() {
        Fpdu fpdu = new Fpdu();
        int phase = buffer.get() & 0xFF;
        int type = buffer.get() & 0xFF;
        fpdu.setFpduType(FpduType.from(phase, type));
        log.debug("Parsing FPDU: phase={}, type={} -> {}", phase, type, fpdu.getFpduType());
        int idDest = buffer.get();
        int idSrc = buffer.get();
        fpdu.setIdDst(idDest);
        fpdu.setIdSrc(idSrc);

        // DTF FPDUs contain raw data, not parameters
        if (fpdu.getFpduType() == FpduType.DTF || fpdu.getFpduType() == FpduType.DTFDA
                || fpdu.getFpduType() == FpduType.DTFMA || fpdu.getFpduType() == FpduType.DTFFA) {
            if (buffer.hasRemaining()) {
                byte[] rawData = new byte[buffer.remaining()];
                buffer.get(rawData);
                fpdu.setData(rawData);
                log.info("{} FPDU contains {} bytes of data", fpdu.getFpduType(), rawData.length);
            }
            return fpdu;
        }

        while (buffer.hasRemaining()) {
            int paramId = buffer.get();
            int paramLength = buffer.get();
            if (paramLength == 0xff) {
                paramLength = buffer.getShort();
            }
            byte[] paramData = new byte[paramLength];
            buffer.get(paramData);
            if (ParameterIdentifier.fromId(paramId) != null) {
                ParameterIdentifier paramIdEnum = ParameterIdentifier.fromId(paramId);
                log.info("PI {} found which is {} and has a size of {} bytes", paramId, paramIdEnum, paramLength);
                ParameterValue paramValue = new ParameterValue(paramIdEnum, paramData);
                fpdu.getParameters().add(paramValue);
            } else if (ParameterGroupIdentifier.fromId(paramId) != null) {
                ParameterGroupIdentifier groupId = ParameterGroupIdentifier.fromId(paramId);
                log.info("PGI {} found which is {}", paramId, groupId);
                ParameterValue groupParameterValue = new ParameterValue(groupId, new ParameterValue[0]);
                fpdu.getParameters().add(groupParameterValue);
                ByteBuffer groupBuffer = ByteBuffer.wrap(paramData);
                while (groupBuffer.hasRemaining()) {
                    int groupParamId = groupBuffer.get();
                    int groupParamLength = groupBuffer.get();
                    byte[] groupParamData = new byte[groupParamLength];
                    groupBuffer.get(groupParamData);
                    ParameterValue groupParamValue = new ParameterValue(ParameterIdentifier.fromId(groupParamId),
                            groupParamData);
                    log.info("PI {} found which is {}", groupParamId, groupParamValue.getParameter());
                    groupParameterValue.getValues().add(groupParamValue);
                }
            } else {
                throw new IllegalArgumentException("Unknown parameter ID: " + paramId);
            }
        }
        return fpdu;
    }
}
