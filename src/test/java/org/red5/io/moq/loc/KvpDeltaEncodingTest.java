package org.red5.io.moq.loc;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.deserialize.LocDeserializer;
import org.red5.io.moq.loc.model.*;
import org.red5.io.moq.loc.serialize.LocSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that LOC header extensions encode type IDs correctly on the wire.
 *
 * LOC extension IDs: CaptureTimestamp=2, VideoFrameMarking=4, AudioLevel=6, VideoConfig=13.
 * Currently the serializer writes absolute type IDs.
 *
 * @see draft-ietf-moq-loc-01 Section 2.3
 */
class KvpDeltaEncodingTest {

    // ─── Serialization: type IDs on the wire ──────────

    @Test
    void singleExtension_absoluteId() throws IOException {
        // Single extension: CaptureTimestamp (ID=2), value=42 → wire: [id=2][value=42]
        LocObject obj = new LocObject(LocObject.MediaType.AUDIO, new byte[0]);
        obj.setCaptureTimestamp(42);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        // First byte: type = 2 (QUIC varint, 1 byte)
        assertEquals(0x02, wire[0], "First extension type should be absolute ID (2)");
    }

    @Test
    void twoEvenExtensions_absoluteIds() throws IOException {
        // CaptureTimestamp (ID=2) then VideoFrameMarking (ID=4).
        // Absolute IDs: 2, 4.
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[0]);
        obj.setCaptureTimestamp(42);
        obj.setVideoFrameMarking(true, false, true, 0, 0);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        // Parse the wire bytes manually to check type IDs
        ByteBuffer buf = ByteBuffer.wrap(wire);

        // Extension 1: CaptureTimestamp — ID = 2
        long id1 = LocHeaderExtension.readVarint(buf);
        assertEquals(2, id1, "First type should be 2 (CaptureTimestamp)");

        // Skip CaptureTimestamp value (even type → varint value)
        LocHeaderExtension.readVarint(buf); // consume value

        // Extension 2: VideoFrameMarking — ID = 4
        long id2 = LocHeaderExtension.readVarint(buf);
        assertEquals(4, id2, "Second type should be 4 (VideoFrameMarking)");
    }

    @Test
    void threeEvenExtensions_absoluteIds() throws IOException {
        // CaptureTimestamp (2) + VideoFrameMarking (4) + AudioLevel (6).
        // Absolute IDs: 2, 4, 6.
        LocObject obj = new LocObject(LocObject.MediaType.AUDIO, new byte[0]);
        obj.setCaptureTimestamp(1000000);
        obj.setVideoFrameMarking(true, false, true, 0, 0);
        obj.setAudioLevel(true, 45);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        ByteBuffer buf = ByteBuffer.wrap(wire);

        // Extension 1: ID = 2
        long id1 = LocHeaderExtension.readVarint(buf);
        assertEquals(2, id1, "First type should be 2");
        LocHeaderExtension.readVarint(buf); // skip value

        // Extension 2: ID = 4
        long id2 = LocHeaderExtension.readVarint(buf);
        assertEquals(4, id2, "Second type should be 4");
        LocHeaderExtension.readVarint(buf); // skip value

        // Extension 3: ID = 6
        long id3 = LocHeaderExtension.readVarint(buf);
        assertEquals(6, id3, "Third type should be 6");
    }

    @Test
    void mixedEvenOdd_absoluteIds() throws IOException {
        // CaptureTimestamp (2) + VideoFrameMarking (4) + VideoConfig (13, odd).
        // Absolute IDs: 2, 4, 13.
        byte[] configData = new byte[]{0x01, 0x42, (byte) 0xC0};

        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[0]);
        obj.setCaptureTimestamp(5000);
        obj.setVideoFrameMarking(true, false, true, 0, 0);
        obj.setVideoConfig(configData);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        ByteBuffer buf = ByteBuffer.wrap(wire);

        // Extension 1: CaptureTimestamp, ID = 2
        long id1 = LocHeaderExtension.readVarint(buf);
        assertEquals(2, id1, "First type should be 2");
        LocHeaderExtension.readVarint(buf); // skip value

        // Extension 2: VideoFrameMarking, ID = 4
        long id2 = LocHeaderExtension.readVarint(buf);
        assertEquals(4, id2, "Second type should be 4");
        LocHeaderExtension.readVarint(buf); // skip value

        // Extension 3: VideoConfig, ID = 13
        long id3 = LocHeaderExtension.readVarint(buf);
        assertEquals(13, id3, "Third type should be 13");
    }

    @Test
    void allFourExtensions_absoluteIds() throws IOException {
        // CaptureTimestamp (2) + VideoFrameMarking (4) + AudioLevel (6) + VideoConfig (13).
        // Absolute IDs: 2, 4, 6, 13.
        byte[] configData = new byte[]{0x01, 0x02};

        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
        obj.setCaptureTimestamp(1702345678000000L);
        obj.setVideoFrameMarking(true, false, true, 0, 0);
        obj.setAudioLevel(false, 30);
        obj.setVideoConfig(configData);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        ByteBuffer buf = ByteBuffer.wrap(wire);

        // Extension 1: ID = 2
        assertEquals(2, LocHeaderExtension.readVarint(buf), "Type 1 should be 2");
        LocHeaderExtension.readVarint(buf); // skip CaptureTimestamp value

        // Extension 2: ID = 4
        assertEquals(4, LocHeaderExtension.readVarint(buf), "Type 2 should be 4");
        LocHeaderExtension.readVarint(buf); // skip VideoFrameMarking value

        // Extension 3: ID = 6
        assertEquals(6, LocHeaderExtension.readVarint(buf), "Type 3 should be 6");
        LocHeaderExtension.readVarint(buf); // skip AudioLevel value

        // Extension 4: ID = 13
        assertEquals(13, LocHeaderExtension.readVarint(buf), "Type 4 should be 13");
        // Odd type: length varint + bytes
        long len = LocHeaderExtension.readVarint(buf);
        assertEquals(2, len, "VideoConfig length should be 2");
    }

    // ─── Deserialization: reconstruct absolute IDs from deltas ──────

    @Test
    void deserialize_singleExtension() throws IOException {
        // Serialize with deltas, deserialize, verify values recovered
        LocObject original = new LocObject(LocObject.MediaType.AUDIO, new byte[10]);
        original.setCaptureTimestamp(9876543210L);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(original);

        LocDeserializer deserializer = new LocDeserializer();
        LocObject restored = deserializer.deserialize(wire, new byte[10],
                LocObject.MediaType.AUDIO);

        assertNotNull(restored.getCaptureTimestamp());
        assertEquals(9876543210L, restored.getCaptureTimestamp().getCaptureTimestampMicros());
    }

    @Test
    void deserialize_twoExtensions_roundTrip() throws IOException {
        LocObject original = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
        original.setCaptureTimestamp(1234567890L);
        original.setVideoFrameMarking(true, false, true, 2, 1);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(original);

        LocDeserializer deserializer = new LocDeserializer();
        LocObject restored = deserializer.deserialize(wire, new byte[100],
                LocObject.MediaType.VIDEO);

        assertNotNull(restored.getCaptureTimestamp());
        assertEquals(1234567890L, restored.getCaptureTimestamp().getCaptureTimestampMicros());

        assertNotNull(restored.getVideoFrameMarking());
        assertTrue(restored.isIndependentFrame());
        assertEquals(2, restored.getVideoFrameMarking().getTemporalLayerId());
        assertEquals(1, restored.getVideoFrameMarking().getSpatialLayerId());
    }

    @Test
    void deserialize_allFourExtensions_roundTrip() throws IOException {
        byte[] configData = new byte[]{0x01, 0x42, (byte) 0xC0, 0x1E};

        LocObject original = new LocObject(LocObject.MediaType.VIDEO, new byte[5000]);
        original.setCaptureTimestamp(1702345678000000L);
        original.setVideoFrameMarking(true, false, true, 0, 0);
        original.setAudioLevel(true, 60);
        original.setVideoConfig(configData);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(original);

        LocDeserializer deserializer = new LocDeserializer();
        LocObject restored = deserializer.deserialize(wire, new byte[5000],
                LocObject.MediaType.VIDEO);

        // CaptureTimestamp
        assertNotNull(restored.getCaptureTimestamp());
        assertEquals(1702345678000000L, restored.getCaptureTimestamp().getCaptureTimestampMicros());

        // VideoFrameMarking
        assertNotNull(restored.getVideoFrameMarking());
        assertTrue(restored.isIndependentFrame());

        // AudioLevel
        assertNotNull(restored.getAudioLevel());
        assertTrue(restored.getAudioLevel().isVoiceActivity());
        assertEquals(60, restored.getAudioLevel().getAudioLevel());

        // VideoConfig
        assertNotNull(restored.getVideoConfig());
        assertArrayEquals(configData, restored.getVideoConfig().getConfigData());
    }

    @Test
    void deserialize_handCraftedAbsoluteWire() throws IOException {
        // Hand-craft wire bytes for CaptureTimestamp(2) + AudioLevel(6).
        // Absolute IDs: 2, 6.
        // CaptureTimestamp value = 42 (1-byte QUIC varint).
        // AudioLevel value = 0x8A = voice=true, level=10 (2-byte QUIC varint for 138).
        byte[] wire = new byte[]{
                0x02,             // type = 2 (CaptureTimestamp)
                0x2A,             // value = 42
                0x06,             // type = 6 (AudioLevel)
                0x40, (byte) 0x8A // value = 138 = 0x80|10 (voice=true, level=10)
        };

        LocDeserializer deserializer = new LocDeserializer();
        LocObject restored = deserializer.deserialize(wire, new byte[0],
                LocObject.MediaType.AUDIO);

        assertNotNull(restored.getCaptureTimestamp());
        assertEquals(42, restored.getCaptureTimestamp().getCaptureTimestampMicros());

        assertNotNull(restored.getAudioLevel());
        assertTrue(restored.getAudioLevel().isVoiceActivity());
        assertEquals(10, restored.getAudioLevel().getAudioLevel());
    }

    // ─── Edge cases ─────────────────────────────────────────────────

    @Test
    void emptyExtensions_producesEmptyWire() throws IOException {
        LocObject obj = new LocObject(LocObject.MediaType.AUDIO, new byte[10]);

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        assertEquals(0, wire.length, "No extensions should produce empty wire");
    }

    @Test
    void extensionsInInsertionOrder() throws IOException {
        // Extensions are serialized in insertion order.
        // Add in ascending ID order: CaptureTimestamp(2), VideoFrameMarking(4), VideoConfig(13).
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[0]);
        obj.setCaptureTimestamp(1000);             // ID 2
        obj.setVideoFrameMarking(true, false, true, 0, 0); // ID 4
        obj.setVideoConfig(new byte[]{0x01});     // ID 13

        LocSerializer serializer = new LocSerializer();
        byte[] wire = serializer.serializeHeaderExtensions(obj);

        ByteBuffer buf = ByteBuffer.wrap(wire);

        // Verify type IDs appear in insertion order
        long prevType = 0;
        while (buf.hasRemaining()) {
            long absType = LocHeaderExtension.readVarint(buf);
            assertTrue(absType > prevType, "Type IDs must be ascending (got " + absType + " after " + prevType + ")");
            prevType = absType;

            boolean isEven = (absType % 2) == 0;
            if (isEven) {
                LocHeaderExtension.readVarint(buf); // skip varint value
            } else {
                long len = LocHeaderExtension.readVarint(buf); // length
                buf.position(buf.position() + (int) len); // skip bytes
            }
        }
    }
}
