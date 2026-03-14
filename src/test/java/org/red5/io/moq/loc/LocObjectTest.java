package org.red5.io.moq.loc;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.deserialize.LocDeserializer;
import org.red5.io.moq.loc.model.*;
import org.red5.io.moq.loc.serialize.LocSerializer;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LOC (Low Overhead Media Container) implementation.
 *
 * Tests serialization and deserialization of LOC objects with various
 * header extensions as defined in draft-ietf-moq-loc.
 */
class LocObjectTest {

    @Test
    void testCaptureTimestampExtension() throws IOException {
        // Create extension with timestamp
        long timestamp = System.currentTimeMillis() * 1000; // Convert to microseconds
        CaptureTimestampExtension ext = new CaptureTimestampExtension(timestamp);

        // Serialize
        byte[] serialized = ext.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Verify the extension can round-trip
        assertEquals(CaptureTimestampExtension.EXTENSION_ID, ext.getExtensionId());
        assertEquals(timestamp, ext.getCaptureTimestampMicros());
    }

    @Test
    void testVideoFrameMarkingExtension() throws IOException {
        // Create extension for an independent frame
        VideoFrameMarkingExtension ext = new VideoFrameMarkingExtension(
                true,  // independent
                false, // not discardable
                true,  // base layer sync
                2,     // temporal layer ID
                1      // spatial layer ID
        );

        // Serialize
        byte[] serialized = ext.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Verify properties
        assertTrue(ext.isIndependent());
        assertFalse(ext.isDiscardable());
        assertTrue(ext.isBaseLayerSync());
        assertEquals(2, ext.getTemporalLayerId());
        assertEquals(1, ext.getSpatialLayerId());
    }

    @Test
    void testAudioLevelExtension() throws IOException {
        // Create extension with voice activity
        AudioLevelExtension ext = new AudioLevelExtension(true, 45);

        // Serialize
        byte[] serialized = ext.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.length > 0);

        // Verify properties
        assertTrue(ext.isVoiceActivity());
        assertEquals(45, ext.getAudioLevel());
    }

    @Test
    void testVideoConfigExtension() throws IOException {
        // Create extension with config data
        byte[] configData = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        VideoConfigExtension ext = new VideoConfigExtension(configData);

        // Serialize
        byte[] serialized = ext.serialize();
        assertNotNull(serialized);
        assertTrue(serialized.length > configData.length); // ID + length + data

        // Verify properties
        assertArrayEquals(configData, ext.getConfigData());
    }

    @Test
    void testMinimalAudioObject() throws IOException {
        // Create minimal audio object
        byte[] audioData = new byte[480]; // 10ms at 48kHz
        long timestamp = System.currentTimeMillis() * 1000;

        LocObject obj = LocSerializer.createMinimalAudioObject(audioData, timestamp);

        // Verify properties
        assertEquals(LocObject.MediaType.AUDIO, obj.getMediaType());
        assertArrayEquals(audioData, obj.getPayload());
        assertNotNull(obj.getCaptureTimestamp());
        assertEquals(timestamp, obj.getCaptureTimestamp().getCaptureTimestampMicros());
    }

    @Test
    void testMinimalVideoObject() throws IOException {
        // Create minimal video object (key frame)
        byte[] videoData = new byte[5000];
        long timestamp = System.currentTimeMillis() * 1000;

        LocObject obj = LocSerializer.createMinimalVideoObject(videoData, timestamp, true);

        // Verify properties
        assertEquals(LocObject.MediaType.VIDEO, obj.getMediaType());
        assertArrayEquals(videoData, obj.getPayload());
        assertNotNull(obj.getCaptureTimestamp());
        assertNotNull(obj.getVideoFrameMarking());
        assertTrue(obj.isIndependentFrame());
        assertFalse(obj.isDiscardableFrame());
    }

    @Test
    void testLocObjectSerialization() throws IOException {
        // Create LOC object with multiple extensions
        byte[] payload = new byte[]{1, 2, 3, 4, 5};
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, payload);

        obj.setGroupId(10);
        obj.setObjectId(5);
        obj.setSubgroupId(0);
        obj.setCaptureTimestamp(1234567890L);
        obj.setVideoFrameMarking(true, false, true, 0, 0);

        // Serialize
        LocSerializer serializer = new LocSerializer();
        byte[] serialized = serializer.serialize(obj);

        assertNotNull(serialized);
        assertTrue(serialized.length > payload.length); // Headers + payload

        // Verify header extensions can be serialized separately
        byte[] headers = serializer.serializeHeaderExtensions(obj);
        assertNotNull(headers);
        assertTrue(headers.length > 0);

        // Verify payload can be retrieved
        byte[] retrievedPayload = serializer.getPayload(obj);
        assertArrayEquals(payload, retrievedPayload);
    }

    @Test
    void testLocObjectDeserialization() throws IOException {
        // Create and serialize an object
        byte[] payload = new byte[]{10, 20, 30, 40, 50};
        LocObject original = new LocObject(LocObject.MediaType.AUDIO, payload);
        original.setCaptureTimestamp(9876543210L);
        original.setAudioLevel(true, 60);

        LocSerializer serializer = new LocSerializer();
        byte[] headerExtensions = serializer.serializeHeaderExtensions(original);

        // Deserialize
        LocDeserializer deserializer = new LocDeserializer();
        LocObject deserialized = deserializer.deserialize(
                headerExtensions,
                payload,
                LocObject.MediaType.AUDIO
        );

        // Verify
        assertEquals(LocObject.MediaType.AUDIO, deserialized.getMediaType());
        assertArrayEquals(payload, deserialized.getPayload());

        // Verify extensions
        assertNotNull(deserialized.getCaptureTimestamp());
        assertEquals(9876543210L, deserialized.getCaptureTimestamp().getCaptureTimestampMicros());

        assertNotNull(deserialized.getAudioLevel());
        assertTrue(deserialized.getAudioLevel().isVoiceActivity());
        assertEquals(60, deserialized.getAudioLevel().getAudioLevel());
    }

    @Test
    void testVideoObjectRoundTrip() throws IOException {
        // Create video object with all extensions
        byte[] payload = new byte[8192];
        byte[] configData = new byte[]{0x01, 0x42, (byte) 0xC0, 0x1E};

        LocObject original = new LocObject(LocObject.MediaType.VIDEO, payload);
        original.setGroupId(100);
        original.setObjectId(0); // First frame in group (key frame)
        original.setCaptureTimestamp(1234567890000L);
        original.setVideoFrameMarking(true, false, true, 0, 0);
        original.setVideoConfig(configData);

        // Serialize
        LocSerializer serializer = new LocSerializer();
        byte[] headerExtensions = serializer.serializeHeaderExtensions(original);

        // Deserialize
        LocDeserializer deserializer = new LocDeserializer();
        LocObject deserialized = deserializer.deserialize(
                headerExtensions,
                payload,
                LocObject.MediaType.VIDEO
        );

        // Verify all properties
        assertEquals(LocObject.MediaType.VIDEO, deserialized.getMediaType());
        assertArrayEquals(payload, deserialized.getPayload());

        // Verify capture timestamp
        assertNotNull(deserialized.getCaptureTimestamp());
        assertEquals(1234567890000L, deserialized.getCaptureTimestamp().getCaptureTimestampMicros());

        // Verify video frame marking
        assertNotNull(deserialized.getVideoFrameMarking());
        assertTrue(deserialized.isIndependentFrame());
        assertFalse(deserialized.isDiscardableFrame());

        // Verify video config
        assertNotNull(deserialized.getVideoConfig());
        assertArrayEquals(configData, deserialized.getVideoConfig().getConfigData());
    }

    @Test
    void testTemporalLayerEncoding() throws IOException {
        // Test temporal layer encoding for SVC/simulcast
        // RFC 9626: SID is only encoded in the second byte when B=1
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
        obj.setVideoFrameMarking(false, false, true, 3, 2); // TID=3, SID=2, B=true required for SID

        LocSerializer serializer = new LocSerializer();
        byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);

        LocDeserializer deserializer = new LocDeserializer();
        LocObject deserialized = deserializer.deserialize(
                headerExtensions,
                new byte[100],
                LocObject.MediaType.VIDEO
        );

        VideoFrameMarkingExtension marking = deserialized.getVideoFrameMarking();
        assertNotNull(marking);
        assertEquals(3, marking.getTemporalLayerId());
        assertEquals(2, marking.getSpatialLayerId());
    }

    @Test
    void testEmptyPayload() throws IOException {
        // Test with empty payload (metadata-only object)
        LocObject obj = new LocObject(LocObject.MediaType.AUDIO, new byte[0]);
        obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);

        LocSerializer serializer = new LocSerializer();
        byte[] serialized = serializer.serialize(obj);

        assertNotNull(serialized);
        assertTrue(serialized.length > 0); // Should have header extensions
    }

    @Test
    void testLargePayload() throws IOException {
        // Test with large payload (1MB)
        byte[] largePayload = new byte[1024 * 1024];
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, largePayload);
        obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);
        obj.setVideoFrameMarking(true, false, true, 0, 0);

        LocSerializer serializer = new LocSerializer();
        byte[] serialized = serializer.serialize(obj);

        assertNotNull(serialized);
        assertTrue(serialized.length >= largePayload.length);
    }

    @Test
    void testInvalidTemporalLayerId() {
        LocObject obj = new LocObject();

        // Temporal layer ID must be 0-7
        assertThrows(IllegalArgumentException.class, () ->
                obj.setVideoFrameMarking(true, false, true, 8, 0));
    }

    @Test
    void testInvalidSpatialLayerId() {
        LocObject obj = new LocObject();

        // Spatial layer ID must be 0-3
        assertThrows(IllegalArgumentException.class, () ->
                obj.setVideoFrameMarking(true, false, true, 0, 4));
    }

    @Test
    void testInvalidAudioLevel() {
        // Audio level must be 0-127
        assertThrows(IllegalArgumentException.class, () ->
                new AudioLevelExtension(true, 128));
    }
}
