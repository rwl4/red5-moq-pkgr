package org.red5.io.moq.loc.model;

import org.red5.io.moq.model.IHeaderExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Base class for LOC (Low Overhead Media Container) Header Extensions.
 *
 * LOC Header Extensions carry optional metadata for the corresponding LOC Payload.
 * These are contained within MOQ Object Header Extensions and provide information
 * for subscribers, relays, and intermediaries without accessing the media payload.
 *
 * Reference: draft-ietf-moq-loc
 * https://datatracker.ietf.org/doc/html/draft-mzanaty-moq-loc-05
 */
public abstract class LocHeaderExtension implements IHeaderExtension {

    /**
     * Extension ID (varint).
     * Even IDs: Value is varint, Length is omitted
     * Odd IDs: Value is Length bytes, Length is varint
     */
    protected final int extensionId;

    public LocHeaderExtension(int extensionId) {
        this.extensionId = extensionId;
    }

    public int getExtensionId() {
        return extensionId;
    }

    /**
     * Check if this extension uses varint encoding for the value.
     * Even IDs use varint, odd IDs use byte array.
     */
    public boolean isVarintValue() {
        return (extensionId % 2) == 0;
    }

    /**
     * Serialize the extension to bytes.
     * Format: [ID][Length (if odd ID)][Value]
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Write extension ID as varint
        writeVarint(baos, extensionId);

        // Get value bytes
        byte[] valueBytes = serializeValue();

        if (!isVarintValue()) {
            // Odd ID: write length as varint
            writeVarint(baos, valueBytes.length);
        }

        // Write value
        baos.write(valueBytes);

        return baos.toByteArray();
    }

    /**
     * Serialize the extension value (subclass-specific).
     */
    protected abstract byte[] serializeValue() throws IOException;

    /**
     * Deserialize the extension value from a buffer.
     */
    public abstract void deserializeValue(ByteBuffer buffer, int length) throws IOException;

    /**
     * Write a QUIC varint to an output stream.
     * QUIC varint format (RFC 9000 Section 16): first 2 bits indicate length.
     * <ul>
     *   <li>00xxxxxx = 1 byte (6-bit value, max 63)</li>
     *   <li>01xxxxxx = 2 bytes (14-bit value, max 16383)</li>
     *   <li>10xxxxxx = 4 bytes (30-bit value, max 1073741823)</li>
     *   <li>11xxxxxx = 8 bytes (62-bit value, max 4611686018427387903)</li>
     * </ul>
     */
    protected void writeVarint(ByteArrayOutputStream baos, long value) throws IOException {
        if (value <= 63) {
            // 1 byte: 00xxxxxx
            baos.write((int) value);
        } else if (value <= 16383) {
            // 2 bytes: 01xxxxxx xxxxxxxx
            baos.write((int) ((value >> 8) | 0x40));
            baos.write((int) (value & 0xFF));
        } else if (value <= 1073741823) {
            // 4 bytes: 10xxxxxx xxxxxxxx xxxxxxxx xxxxxxxx
            baos.write((int) ((value >> 24) | 0x80));
            baos.write((int) ((value >> 16) & 0xFF));
            baos.write((int) ((value >> 8) & 0xFF));
            baos.write((int) (value & 0xFF));
        } else {
            // 8 bytes: 11xxxxxx xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxxx xxxxxxxx
            baos.write((int) ((value >> 56) | 0xC0));
            baos.write((int) ((value >> 48) & 0xFF));
            baos.write((int) ((value >> 40) & 0xFF));
            baos.write((int) ((value >> 32) & 0xFF));
            baos.write((int) ((value >> 24) & 0xFF));
            baos.write((int) ((value >> 16) & 0xFF));
            baos.write((int) ((value >> 8) & 0xFF));
            baos.write((int) (value & 0xFF));
        }
    }

    /**
     * Read a QUIC varint from a ByteBuffer.
     * QUIC varint format (RFC 9000 Section 16): first 2 bits indicate length.
     */
    public static long readVarint(ByteBuffer buffer) throws IOException {
        if (!buffer.hasRemaining()) {
            throw new IOException("Unexpected end of buffer while reading varint");
        }

        byte firstByte = buffer.get();
        int lengthType = (firstByte & 0xC0) >> 6;

        switch (lengthType) {
            case 0:
                // 1 byte: 00xxxxxx (6-bit value)
                return firstByte & 0x3F;
            case 1:
                // 2 bytes: 01xxxxxx xxxxxxxx (14-bit value)
                if (!buffer.hasRemaining()) {
                    throw new IOException("Unexpected end of buffer while reading 2-byte varint");
                }
                return ((firstByte & 0x3F) << 8) | (buffer.get() & 0xFF);
            case 2:
                // 4 bytes: 10xxxxxx xxxxxxxx xxxxxxxx xxxxxxxx (30-bit value)
                if (buffer.remaining() < 3) {
                    throw new IOException("Unexpected end of buffer while reading 4-byte varint");
                }
                return ((long) (firstByte & 0x3F) << 24) |
                       ((buffer.get() & 0xFF) << 16) |
                       ((buffer.get() & 0xFF) << 8) |
                       (buffer.get() & 0xFF);
            case 3:
                // 8 bytes: 11xxxxxx xxxxxxxx... (62-bit value)
                if (buffer.remaining() < 7) {
                    throw new IOException("Unexpected end of buffer while reading 8-byte varint");
                }
                return ((long) (firstByte & 0x3F) << 56) |
                       ((long) (buffer.get() & 0xFF) << 48) |
                       ((long) (buffer.get() & 0xFF) << 40) |
                       ((long) (buffer.get() & 0xFF) << 32) |
                       ((long) (buffer.get() & 0xFF) << 24) |
                       ((buffer.get() & 0xFF) << 16) |
                       ((buffer.get() & 0xFF) << 8) |
                       (buffer.get() & 0xFF);
            default:
                throw new IOException("Invalid varint length type: " + lengthType);
        }
    }

    /**
     * Serialize a varint value to bytes.
     */
    protected byte[] serializeVarint(long value) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeVarint(baos, value);
        return baos.toByteArray();
    }

}
