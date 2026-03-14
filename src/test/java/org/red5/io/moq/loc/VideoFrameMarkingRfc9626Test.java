package org.red5.io.moq.loc;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.model.VideoFrameMarkingExtension;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that VideoFrameMarkingExtension uses the correct RFC 9626 bit layout.
 *
 * RFC 9626 defines the Video Frame Marking RTP Header Extension with this
 * bit layout in the first byte:
 *
 *   Bit 7: S — Start of frame
 *   Bit 6: E — End of frame
 *   Bit 5: I — Independent frame (keyframe)
 *   Bit 4: D — Discardable frame
 *   Bit 3: B — Base layer sync
 *   Bits 2-0: TID — Temporal layer ID (0-7)
 *
 * LOC §2.3.2.2 says this is "encoded in the least significant bits of a varint",
 * meaning the RFC 9626 byte layout is placed directly into the varint value.
 *
 * A keyframe (I=1, B=1) should produce value 0x28 = 0b00101000, NOT 0x05 = 0b00000101.
 *
 * @see RFC 9626 (Video Frame Marking RTP Header Extension)
 * @see draft-ietf-moq-loc-01 §2.3.2.2
 */
class VideoFrameMarkingRfc9626Test {

    // ── Wire format tests (RED — these assert the correct RFC 9626 layout) ──

    @Test
    void keyframeProducesCorrectWireValue() throws IOException {
        // A keyframe: independent=true, baseLayerSync=true, all else default
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                true,   // independent (I) — RFC 9626 bit 5 = 0x20
                false,  // discardable (D)
                true,   // baseLayerSync (B) — RFC 9626 bit 3 = 0x08
                0,      // temporalLayerId
                0       // spatialLayerId
        );

        byte[] serialized = ext.serialize();
        // Extension ID 4 is even → varint type ID + varint value (no length field)
        // Type ID 4 encodes as single byte: 0x04
        // Value should be 0x28 = I(0x20) | B(0x08)
        assertEquals(2, serialized.length, "type varint (1 byte) + value varint (1 byte)");
        assertEquals(0x04, serialized[0] & 0xFF, "extension type ID");
        assertEquals(0x28, serialized[1] & 0xFF,
                "keyframe value: I(0x20) | B(0x08) = 0x28 per RFC 9626");
    }

    @Test
    void nonKeyframeProducesZeroValue() throws IOException {
        // A non-keyframe: all flags false, TID=0
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                false, false, false, 0, 0);

        byte[] serialized = ext.serialize();
        assertEquals(2, serialized.length);
        assertEquals(0x04, serialized[0] & 0xFF);
        assertEquals(0x00, serialized[1] & 0xFF, "non-keyframe: all bits zero");
    }

    @Test
    void discardableFrameUsesCorrectBit() throws IOException {
        // Discardable only: D = bit 4 = 0x10
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                false, true, false, 0, 0);

        byte[] serialized = ext.serialize();
        assertEquals(0x10, serialized[1] & 0xFF,
                "discardable: D(0x10) per RFC 9626 bit 4");
    }

    @Test
    void temporalLayerIdUsesCorrectBits() throws IOException {
        // TID=5: bits 2-0 = 0x05
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                false, false, false, 5, 0);

        byte[] serialized = ext.serialize();
        assertEquals(0x05, serialized[1] & 0xFF,
                "TID=5 in bits 2-0");
    }

    @Test
    void allFlagsSetProducesCorrectValue() throws IOException {
        // I + D + B + TID=3: 0x20 | 0x10 | 0x08 | 0x03 = 0x3B
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                true, true, true, 3, 0);

        byte[] serialized = ext.serialize();
        assertEquals(0x3B, serialized[1] & 0xFF,
                "I(0x20) | D(0x10) | B(0x08) | TID=3 = 0x3B");
    }

    @Test
    void deserializeKeyframeRoundTrip() throws IOException {
        // Serialize a keyframe, then deserialize and verify flags
        VideoFrameMarkingExtension original = new VideoFrameMarkingExtension(
                true, false, true, 0, 0);

        byte[] serialized = original.serialize();

        // Deserialize: skip the type ID byte, read just the value
        VideoFrameMarkingExtension deserialized = new VideoFrameMarkingExtension();
        ByteBuffer buf = ByteBuffer.wrap(serialized, 1, serialized.length - 1);
        deserialized.deserializeValue(buf, serialized.length - 1);

        assertTrue(deserialized.isIndependent(), "round-trip: independent should be true");
        assertFalse(deserialized.isDiscardable(), "round-trip: discardable should be false");
        assertTrue(deserialized.isBaseLayerSync(), "round-trip: baseLayerSync should be true");
        assertEquals(0, deserialized.getTemporalLayerId(), "round-trip: TID should be 0");
    }

    @Test
    void deserializeDiscardableRoundTrip() throws IOException {
        VideoFrameMarkingExtension original = new VideoFrameMarkingExtension(
                false, true, false, 7, 0);

        byte[] serialized = original.serialize();

        VideoFrameMarkingExtension deserialized = new VideoFrameMarkingExtension();
        ByteBuffer buf = ByteBuffer.wrap(serialized, 1, serialized.length - 1);
        deserialized.deserializeValue(buf, serialized.length - 1);

        assertFalse(deserialized.isIndependent());
        assertTrue(deserialized.isDiscardable());
        assertFalse(deserialized.isBaseLayerSync());
        assertEquals(7, deserialized.getTemporalLayerId());
    }

    @Test
    void interopWithStandardLocParser() throws IOException {
        // A standard LOC parser reads a keyframe as varint value 0x28.
        // Verify that deserializing 0x28 gives independent=true, baseLayerSync=true.
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension();
        // Simulate receiving 0x28 from the wire
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x28});
        ext.deserializeValue(buf, 1);

        assertTrue(ext.isIndependent(),
                "0x28 from wire: I bit (0x20) should be set");
        assertFalse(ext.isDiscardable(),
                "0x28 from wire: D bit (0x10) should NOT be set");
        assertTrue(ext.isBaseLayerSync(),
                "0x28 from wire: B bit (0x08) should be set");
        assertEquals(0, ext.getTemporalLayerId(),
                "0x28 from wire: TID bits (0x07) should be 0");
    }
}
