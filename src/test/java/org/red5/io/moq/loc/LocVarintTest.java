package org.red5.io.moq.loc;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.model.CaptureTimestampExtension;
import org.red5.io.moq.loc.model.LocHeaderExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that LOC header extension varints use QUIC variable-length integer
 * encoding per RFC 9000 Section 16.
 *
 * Test vectors from RFC 9000 Appendix A.1 and boundary values.
 */
class LocVarintTest {

    // ─── Wire format tests (RFC 9000 Section 16) ─────────────────────

    @Test
    void testVarint1Byte_zero() throws IOException {
        // 0 → 0x00
        assertVarintRoundTrip(0, new byte[]{0x00});
    }

    @Test
    void testVarint1Byte_max() throws IOException {
        // 63 → 0x3F (max 6-bit value)
        assertVarintRoundTrip(63, new byte[]{0x3F});
    }

    @Test
    void testVarint2Byte_min() throws IOException {
        // 64 → 0x40 0x40 (first value requiring 2 bytes)
        assertVarintRoundTrip(64, new byte[]{0x40, 0x40});
    }

    @Test
    void testVarint2Byte_max() throws IOException {
        // 16383 → 0x7F 0xFF (max 14-bit value)
        assertVarintRoundTrip(16383, new byte[]{0x7F, (byte) 0xFF});
    }

    @Test
    void testVarint4Byte_min() throws IOException {
        // 16384 → 0x80 0x00 0x40 0x00
        assertVarintRoundTrip(16384, new byte[]{(byte) 0x80, 0x00, 0x40, 0x00});
    }

    @Test
    void testVarint4Byte_max() throws IOException {
        // 1073741823 → 0xBF 0xFF 0xFF 0xFF (max 30-bit value)
        assertVarintRoundTrip(1073741823L, new byte[]{(byte) 0xBF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
    }

    @Test
    void testVarint8Byte_min() throws IOException {
        // 1073741824 → 0xC0 0x00 0x00 0x00 0x40 0x00 0x00 0x00
        assertVarintRoundTrip(1073741824L, new byte[]{
                (byte) 0xC0, 0x00, 0x00, 0x00, 0x40, 0x00, 0x00, 0x00});
    }

    @Test
    void testVarint8Byte_captureTimestamp() throws IOException {
        // Typical CaptureTimestamp: 1741824000000000 µs (2025-03-13 wall-clock)
        // This value requires 8 bytes in QUIC varint encoding.
        long ts = 1741824000000000L;
        assertVarintRoundTrip(ts);
    }

    // ─── CaptureTimestamp round-trip with known wire format ──────────

    @Test
    void testCaptureTimestampExtension_smallValue() throws IOException {
        // Extension ID 2 (1 byte: 0x02) + value 42 (1 byte: 0x2A)
        CaptureTimestampExtension ext = new CaptureTimestampExtension(42);
        byte[] serialized = ext.serialize();
        assertArrayEquals(new byte[]{0x02, 0x2A}, serialized);
    }

    @Test
    void testCaptureTimestampExtension_wallClockRoundTrip() throws IOException {
        // Wall-clock microseconds since Unix epoch
        long wallClockUs = System.currentTimeMillis() * 1000;
        CaptureTimestampExtension ext = new CaptureTimestampExtension(wallClockUs);
        byte[] serialized = ext.serialize();

        // Extension ID 2 = 1 byte, wall-clock timestamp = 8 bytes → 9 bytes total
        assertEquals(9, serialized.length,
                "Wall-clock CaptureTimestamp should be 9 bytes (1 ID + 8 value)");

        // First byte is extension ID 2
        assertEquals(0x02, serialized[0]);

        // Value bytes should have 8-byte QUIC varint prefix (11xxxxxx)
        assertEquals(0xC0, serialized[1] & 0xC0,
                "Wall-clock timestamp should use 8-byte QUIC varint (prefix 11)");

        // Deserialize and verify round-trip
        ByteBuffer buf = ByteBuffer.wrap(serialized, 1, serialized.length - 1);
        long decoded = LocHeaderExtension.readVarint(buf);
        assertEquals(wallClockUs, decoded);
    }

    // ─── Helpers ─────────────────────────────────────────────────────

    /**
     * Verify varint encode → decode round-trip AND exact wire bytes.
     */
    private void assertVarintRoundTrip(long value, byte[] expectedBytes) throws IOException {
        // Encode
        LocHeaderExtension ext = new CaptureTimestampExtension(0); // just for access to writeVarint
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Use reflection-free approach: serialize a CaptureTimestamp and check the value portion
        byte[] encoded = new CaptureTimestampExtension(value).serialize();
        // Skip the extension ID byte (0x02) to get just the value bytes
        byte[] valueBytes = new byte[encoded.length - 1];
        System.arraycopy(encoded, 1, valueBytes, 0, valueBytes.length);

        assertArrayEquals(expectedBytes, valueBytes,
                String.format("Varint encoding of %d should match expected wire bytes", value));

        // Decode
        ByteBuffer buf = ByteBuffer.wrap(expectedBytes);
        long decoded = LocHeaderExtension.readVarint(buf);
        assertEquals(value, decoded,
                String.format("Varint decoding should produce %d", value));
    }

    /**
     * Verify varint encode → decode round-trip (no expected bytes).
     */
    private void assertVarintRoundTrip(long value) throws IOException {
        byte[] encoded = new CaptureTimestampExtension(value).serialize();
        byte[] valueBytes = new byte[encoded.length - 1];
        System.arraycopy(encoded, 1, valueBytes, 0, valueBytes.length);

        ByteBuffer buf = ByteBuffer.wrap(valueBytes);
        long decoded = LocHeaderExtension.readVarint(buf);
        assertEquals(value, decoded,
                String.format("Round-trip varint for %d failed", value));
    }
}
