package org.red5.io.moq.loc;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.model.AudioLevelExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that AudioLevelExtension bit layout matches RFC 6464 Section 3.
 *
 * RFC 6464 Section 3 defines:
 *   Bit 7 (MSB): V — Voice activity flag (1 = voice, 0 = silence)
 *   Bits 6-0:    level — Audio magnitude in -dBov (0 = loudest, 127 = silence)
 *
 * The LOC spec (draft-ietf-moq-loc-01 Section 2.3.3.1) says the value is
 * "as defined in section 3 of [RFC6464], encoded in the least significant
 * 8 bits of a varint."
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6464#section-3">RFC 6464 §3</a>
 * @see draft-ietf-moq-loc-01 Section 2.3.3.1
 */
class AudioLevelWireFormatTest {

    @Test
    void testVoiceActivityInMSB() throws IOException {
        // Voice active, level 0 (loudest) → byte = 0x80 (V=1, level=0)
        AudioLevelExtension ext = new AudioLevelExtension(true, 0);
        byte[] serialized = ext.serialize();

        // Extension ID 6 = 0x06 (1 byte), value 0x80 = 0x40 0x80 (2-byte QUIC varint)
        assertEquals(0x06, serialized[0], "Extension ID should be 6");
        // Value 0x80 (128) requires 2-byte QUIC varint: 0x40|0x00, 0x80
        assertEquals((byte) 0x40, serialized[1], "2-byte QUIC varint high byte for 128");
        assertEquals((byte) 0x80, serialized[2], "2-byte QUIC varint low byte for 128");
    }

    @Test
    void testNoVoiceActivitySilence() throws IOException {
        // No voice, level 127 (silence) → byte = 0x7F (V=0, level=127)
        AudioLevelExtension ext = new AudioLevelExtension(false, 127);
        byte[] serialized = ext.serialize();

        // Extension ID 6 = 0x06, value 0x7F = single-byte QUIC varint
        assertEquals(0x06, serialized[0]);
        // 0x7F fits in 1-byte QUIC varint (≤63? No, 127 > 63 → 2-byte)
        // 127 requires 2-byte QUIC varint: 0x40|0x00, 0x7F
        assertEquals((byte) 0x40, serialized[1], "2-byte QUIC varint high byte for 127");
        assertEquals((byte) 0x7F, serialized[2], "2-byte QUIC varint low byte for 127");
    }

    @Test
    void testVoiceActivityWithLevel() throws IOException {
        // Voice active, level 45 → byte = 0x80 | 45 = 0xAD (173)
        AudioLevelExtension ext = new AudioLevelExtension(true, 45);
        byte[] serialized = ext.serialize();

        assertEquals(0x06, serialized[0]);
        // Value 173 requires 2-byte QUIC varint: 0x40|0x00, 0xAD
        assertEquals((byte) 0x40, serialized[1]);
        assertEquals((byte) 0xAD, serialized[2]);
    }

    @Test
    void testNoVoiceSmallLevel() throws IOException {
        // No voice, level 10 → byte = 0x0A (10, fits in 1-byte QUIC varint)
        AudioLevelExtension ext = new AudioLevelExtension(false, 10);
        byte[] serialized = ext.serialize();

        assertEquals(0x06, serialized[0]);
        assertEquals(0x0A, serialized[1], "Level 10 with no voice should be 0x0A");
        assertEquals(2, serialized.length, "Should be 2 bytes total (ID + 1-byte value)");
    }

    @Test
    void testRoundTrip_voiceActive() throws IOException {
        AudioLevelExtension original = new AudioLevelExtension(true, 60);
        byte[] serialized = original.serialize();

        // Deserialize via full LOC round-trip
        org.red5.io.moq.loc.model.LocObject obj = new org.red5.io.moq.loc.model.LocObject(
                org.red5.io.moq.loc.model.LocObject.MediaType.AUDIO, new byte[0]);
        obj.setAudioLevel(true, 60);

        var serializer = new org.red5.io.moq.loc.serialize.LocSerializer();
        byte[] headers = serializer.serializeHeaderExtensions(obj);

        var deserializer = new org.red5.io.moq.loc.deserialize.LocDeserializer();
        var deserialized = deserializer.deserialize(headers, new byte[0],
                org.red5.io.moq.loc.model.LocObject.MediaType.AUDIO);

        assertNotNull(deserialized.getAudioLevel());
        assertTrue(deserialized.getAudioLevel().isVoiceActivity());
        assertEquals(60, deserialized.getAudioLevel().getAudioLevel());
    }

    @Test
    void testRoundTrip_noVoice() throws IOException {
        var obj = new org.red5.io.moq.loc.model.LocObject(
                org.red5.io.moq.loc.model.LocObject.MediaType.AUDIO, new byte[0]);
        obj.setAudioLevel(false, 100);

        var serializer = new org.red5.io.moq.loc.serialize.LocSerializer();
        byte[] headers = serializer.serializeHeaderExtensions(obj);

        var deserializer = new org.red5.io.moq.loc.deserialize.LocDeserializer();
        var deserialized = deserializer.deserialize(headers, new byte[0],
                org.red5.io.moq.loc.model.LocObject.MediaType.AUDIO);

        assertNotNull(deserialized.getAudioLevel());
        assertFalse(deserialized.getAudioLevel().isVoiceActivity());
        assertEquals(100, deserialized.getAudioLevel().getAudioLevel());
    }
}
