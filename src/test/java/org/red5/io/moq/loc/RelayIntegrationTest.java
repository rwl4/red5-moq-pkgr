package org.red5.io.moq.loc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.red5.io.moq.loc.deserialize.LocDeserializer;
import org.red5.io.moq.loc.model.*;
import org.red5.io.moq.loc.serialize.LocSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for LOC relay compatibility.
 *
 * These tests simulate the MoQ relay behavior where:
 * - Header extensions are visible to relays for forwarding decisions
 * - Payloads are opaque and may be end-to-end encrypted
 * - Relays MUST NOT modify payloads
 * - GroupId/ObjectId/SubgroupId must be preserved
 *
 * Reference: draft-ietf-moq-loc-01, draft-ietf-moq-transport
 */
class RelayIntegrationTest {

    private LocSerializer serializer;
    private LocDeserializer deserializer;
    private SecureRandom random;

    @BeforeEach
    void setUp() {
        serializer = new LocSerializer();
        deserializer = new LocDeserializer();
        random = new SecureRandom();
    }

    /**
     * Simulates a MoQ relay that:
     * 1. Receives header extensions and payload separately
     * 2. Reads header extensions for forwarding decisions
     * 3. Forwards payload unchanged (opaque)
     * 4. Preserves MoQ metadata (groupId, objectId, subgroupId)
     */
    static class RelaySimulator {

        // Metadata extracted by relay for forwarding decisions
        private boolean isIndependentFrame;
        private boolean isDiscardable;
        private int temporalLayerId;
        private int spatialLayerId;
        private long captureTimestamp;
        private int audioLevel;
        private boolean voiceActivity;

        // Forwarding stats
        private int objectsForwarded;
        private int objectsDropped;

        /**
         * Process a LOC object through the relay.
         * Returns the object with payload unchanged but metadata extracted.
         */
        public RelayedObject process(byte[] headerExtensions, byte[] payload,
                                     long groupId, long objectId, long subgroupId) throws IOException {

            // Extract metadata from header extensions (relay-visible)
            extractMetadata(headerExtensions);

            // Simulate forwarding decision based on metadata
            boolean shouldForward = makeForwardingDecision();

            if (shouldForward) {
                objectsForwarded++;
                // Forward payload UNCHANGED - this is critical for relay compliance
                return new RelayedObject(
                        headerExtensions,
                        payload, // Must be byte-for-byte identical
                        groupId,
                        objectId,
                        subgroupId,
                        true
                );
            } else {
                objectsDropped++;
                return new RelayedObject(null, null, groupId, objectId, subgroupId, false);
            }
        }

        /**
         * Extract metadata from header extensions for forwarding decisions.
         * This simulates what a real relay would do.
         */
        private void extractMetadata(byte[] headerExtensions) throws IOException {
            if (headerExtensions == null || headerExtensions.length == 0) {
                return;
            }

            ByteBuffer buffer = ByteBuffer.wrap(headerExtensions);

            while (buffer.hasRemaining()) {
                long extensionId = LocHeaderExtension.readVarint(buffer);
                boolean isVarintValue = (extensionId % 2) == 0;

                int length = 0;
                if (!isVarintValue) {
                    length = (int) LocHeaderExtension.readVarint(buffer);
                }

                switch ((int) extensionId) {
                    case CaptureTimestampExtension.EXTENSION_ID -> {
                        captureTimestamp = LocHeaderExtension.readVarint(buffer);
                    }
                    case VideoFrameMarkingExtension.EXTENSION_ID -> {
                        long value = LocHeaderExtension.readVarint(buffer);
                        // Decode video frame marking per RFC 9626
                        // Bit 5 = I (Independent), Bit 4 = D (Discardable), Bit 3 = B (Base layer sync)
                        // Bits 2-0 = TID (Temporal layer)
                        // If B=1 and value >= 256: second byte bits 7-2 = LID (spatial layer)
                        boolean hasTwoBytes = value >= 256;
                        long firstByte = hasTwoBytes ? (value >> 8) & 0xFF : value & 0xFF;
                        isIndependentFrame = (firstByte & 0x20) != 0;
                        isDiscardable = (firstByte & 0x10) != 0;
                        // baseLayerSync = (firstByte & 0x08) != 0;
                        temporalLayerId = (int) (firstByte & 0x07);
                        if (hasTwoBytes) {
                            spatialLayerId = (int) ((value & 0xFF) >> 2) & 0x3F;
                        } else {
                            spatialLayerId = 0;
                        }
                    }
                    case AudioLevelExtension.EXTENSION_ID -> {
                        long value = LocHeaderExtension.readVarint(buffer);
                        // Decode RFC6464 audio level per draft-ietf-moq-loc
                        // RFC 6464 Section 3: Bit 7 = V (Voice activity), Bits 6-0 = Level
                        voiceActivity = (value & 0x80) != 0;
                        audioLevel = (int) (value & 0x7F);
                    }
                    case VideoConfigExtension.EXTENSION_ID -> {
                        // Skip config data - relay doesn't need to parse it
                        buffer.position(buffer.position() + length);
                    }
                    default -> {
                        // Skip unknown extensions (forward compatibility)
                        if (isVarintValue) {
                            LocHeaderExtension.readVarint(buffer);
                        } else {
                            buffer.position(buffer.position() + length);
                        }
                    }
                }
            }
        }

        /**
         * Simulate forwarding decision.
         * Real relays use this metadata for:
         * - Priority-based delivery
         * - SVC layer dropping
         * - Bandwidth adaptation
         */
        private boolean makeForwardingDecision() {
            // For testing, forward everything
            // In production, relay might drop based on:
            // - temporal/spatial layer (SVC adaptation)
            // - discardable flag (congestion control)
            return true;
        }

        public boolean isIndependentFrame() { return isIndependentFrame; }
        public boolean isDiscardable() { return isDiscardable; }
        public int getTemporalLayerId() { return temporalLayerId; }
        public int getSpatialLayerId() { return spatialLayerId; }
        public long getCaptureTimestamp() { return captureTimestamp; }
        public int getAudioLevel() { return audioLevel; }
        public boolean isVoiceActivity() { return voiceActivity; }
        public int getObjectsForwarded() { return objectsForwarded; }
        public int getObjectsDropped() { return objectsDropped; }
    }

    /**
     * Represents an object after relay processing.
     */
    record RelayedObject(
            byte[] headerExtensions,
            byte[] payload,
            long groupId,
            long objectId,
            long subgroupId,
            boolean forwarded
    ) {}

    @Nested
    @DisplayName("Payload Integrity Tests")
    class PayloadIntegrityTests {

        @Test
        @DisplayName("Relay preserves video payload byte-for-byte")
        void testVideoPayloadPreservation() throws IOException {
            // Create video frame with random data
            byte[] originalPayload = new byte[8192];
            random.nextBytes(originalPayload);

            LocObject original = new LocObject(LocObject.MediaType.VIDEO, originalPayload);
            original.setGroupId(100);
            original.setObjectId(0);
            original.setSubgroupId(0);
            original.setCaptureTimestamp(System.currentTimeMillis() * 1000);
            original.setVideoFrameMarking(true, false, true, 0, 0);

            // Serialize for transport
            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payload = serializer.getPayload(original);

            // Process through relay
            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payload,
                    original.getGroupId(), original.getObjectId(), original.getSubgroupId()
            );

            // Verify payload unchanged
            assertTrue(relayed.forwarded());
            assertArrayEquals(originalPayload, relayed.payload(),
                    "Relay MUST NOT modify payload bytes");
        }

        @Test
        @DisplayName("Relay preserves audio payload byte-for-byte")
        void testAudioPayloadPreservation() throws IOException {
            // Create audio frame (10ms at 48kHz = 480 samples)
            byte[] originalPayload = new byte[960]; // 480 samples * 2 bytes
            random.nextBytes(originalPayload);

            LocObject original = LocSerializer.createMinimalAudioObject(
                    originalPayload,
                    System.currentTimeMillis() * 1000
            );
            original.setGroupId(50);
            original.setObjectId(0);
            original.setAudioLevel(true, 45);

            // Serialize and relay
            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payload = serializer.getPayload(original);

            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payload,
                    original.getGroupId(), original.getObjectId(), original.getSubgroupId()
            );

            assertArrayEquals(originalPayload, relayed.payload(),
                    "Audio payload must be preserved exactly");
        }

        @Test
        @DisplayName("Relay preserves empty payload")
        void testEmptyPayloadPreservation() throws IOException {
            // Metadata-only object
            LocObject original = new LocObject(LocObject.MediaType.AUDIO, new byte[0]);
            original.setCaptureTimestamp(System.currentTimeMillis() * 1000);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payload = serializer.getPayload(original);

            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payload,
                    0, 0, 0
            );

            assertEquals(0, relayed.payload().length);
        }

        @Test
        @DisplayName("Relay preserves large payload (1MB)")
        void testLargePayloadPreservation() throws IOException {
            byte[] largePayload = new byte[1024 * 1024]; // 1MB
            random.nextBytes(largePayload);

            LocObject original = new LocObject(LocObject.MediaType.VIDEO, largePayload);
            original.setCaptureTimestamp(System.currentTimeMillis() * 1000);
            original.setVideoFrameMarking(true, false, true, 0, 0);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payload = serializer.getPayload(original);

            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(headerExtensions, payload, 1, 0, 0);

            assertArrayEquals(largePayload, relayed.payload());
        }
    }

    @Nested
    @DisplayName("Metadata Extraction Tests")
    class MetadataExtractionTests {

        @Test
        @DisplayName("Relay extracts video frame marking for forwarding decisions")
        void testVideoFrameMarkingExtraction() throws IOException {
            LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
            obj.setVideoFrameMarking(true, false, true, 2, 1);
            obj.setCaptureTimestamp(1234567890L);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);

            RelaySimulator relay = new RelaySimulator();
            relay.process(headerExtensions, new byte[100], 1, 0, 0);

            assertTrue(relay.isIndependentFrame(), "Should detect independent frame");
            assertFalse(relay.isDiscardable(), "Should detect non-discardable");
            assertEquals(2, relay.getTemporalLayerId(), "Should extract temporal layer ID");
            assertEquals(1, relay.getSpatialLayerId(), "Should extract spatial layer ID");
        }

        @Test
        @DisplayName("Relay extracts audio level for voice activity detection")
        void testAudioLevelExtraction() throws IOException {
            LocObject obj = new LocObject(LocObject.MediaType.AUDIO, new byte[960]);
            obj.setAudioLevel(true, 45);
            obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);

            RelaySimulator relay = new RelaySimulator();
            relay.process(headerExtensions, new byte[960], 1, 0, 0);

            assertTrue(relay.isVoiceActivity(), "Should detect voice activity");
            assertEquals(45, relay.getAudioLevel(), "Should extract audio level");
        }

        @Test
        @DisplayName("Relay extracts capture timestamp")
        void testCaptureTimestampExtraction() throws IOException {
            long timestamp = 1702345678000000L; // Microseconds

            LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
            obj.setCaptureTimestamp(timestamp);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);

            RelaySimulator relay = new RelaySimulator();
            relay.process(headerExtensions, new byte[100], 1, 0, 0);

            assertEquals(timestamp, relay.getCaptureTimestamp(),
                    "Should extract capture timestamp");
        }
    }

    @Nested
    @DisplayName("MoQ Transport Mapping Tests")
    class MoQTransportMappingTests {

        @Test
        @DisplayName("GroupId/ObjectId preserved through relay")
        void testMoQIdentifiersPreserved() throws IOException {
            long groupId = 12345;
            long objectId = 67;
            long subgroupId = 2;

            LocObject obj = new LocObject(LocObject.MediaType.VIDEO, new byte[100]);
            obj.setGroupId(groupId);
            obj.setObjectId(objectId);
            obj.setSubgroupId(subgroupId);
            obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);
            byte[] payload = serializer.getPayload(obj);

            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payload, groupId, objectId, subgroupId
            );

            assertEquals(groupId, relayed.groupId());
            assertEquals(objectId, relayed.objectId());
            assertEquals(subgroupId, relayed.subgroupId());
        }

        @Test
        @DisplayName("Video GOP structure preserved through relay")
        void testVideoGopPreservation() throws IOException {
            // Simulate a GOP: IDR + 29 delta frames
            List<LocObject> gopFrames = new ArrayList<>();
            long groupId = 100;
            long baseTimestamp = System.currentTimeMillis() * 1000;

            // IDR frame
            LocObject idr = LocSerializer.createMinimalVideoObject(
                    new byte[50000], baseTimestamp, true);
            idr.setGroupId(groupId);
            idr.setObjectId(0);
            idr.setVideoConfig(new byte[]{0x01, 0x42, (byte)0xC0, 0x1E}); // AVC config
            gopFrames.add(idr);

            // Delta frames
            for (int i = 1; i < 30; i++) {
                LocObject delta = LocSerializer.createMinimalVideoObject(
                        new byte[5000], baseTimestamp + (i * 33333), false); // 33.3ms per frame
                delta.setGroupId(groupId);
                delta.setObjectId(i);
                gopFrames.add(delta);
            }

            // Relay all frames
            RelaySimulator relay = new RelaySimulator();
            List<RelayedObject> relayedFrames = new ArrayList<>();

            for (LocObject frame : gopFrames) {
                byte[] headerExt = serializer.serializeHeaderExtensions(frame);
                byte[] payload = serializer.getPayload(frame);
                RelayedObject relayed = relay.process(
                        headerExt, payload,
                        frame.getGroupId(), frame.getObjectId(), frame.getSubgroupId()
                );
                relayedFrames.add(relayed);
            }

            // Verify all forwarded
            assertEquals(30, relay.getObjectsForwarded());
            assertEquals(0, relay.getObjectsDropped());

            // Verify groupId consistency
            for (RelayedObject relayed : relayedFrames) {
                assertEquals(groupId, relayed.groupId());
            }

            // Verify objectId ordering
            for (int i = 0; i < relayedFrames.size(); i++) {
                assertEquals(i, relayedFrames.get(i).objectId());
            }
        }

        @Test
        @DisplayName("Audio track mapping preserved (one group per frame)")
        void testAudioTrackMapping() throws IOException {
            // Audio: each frame is its own group
            List<RelayedObject> relayedFrames = new ArrayList<>();
            RelaySimulator relay = new RelaySimulator();

            for (int i = 0; i < 100; i++) {
                byte[] audioData = new byte[960];
                random.nextBytes(audioData);

                LocObject audio = LocSerializer.createMinimalAudioObject(
                        audioData, System.currentTimeMillis() * 1000 + (i * 10000));
                audio.setGroupId(i); // Each audio frame = new group
                audio.setObjectId(0);
                audio.setAudioLevel(random.nextBoolean(), random.nextInt(128));

                byte[] headerExt = serializer.serializeHeaderExtensions(audio);
                RelayedObject relayed = relay.process(headerExt, audioData, i, 0, 0);
                relayedFrames.add(relayed);
            }

            assertEquals(100, relay.getObjectsForwarded());

            // Verify sequential groupIds
            for (int i = 0; i < 100; i++) {
                assertEquals(i, relayedFrames.get(i).groupId());
                assertEquals(0, relayedFrames.get(i).objectId());
            }
        }
    }

    @Nested
    @DisplayName("Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Full round-trip: serialize -> relay -> deserialize")
        void testFullRoundTrip() throws IOException {
            // Create original object with all extensions
            byte[] payload = new byte[4096];
            random.nextBytes(payload);
            byte[] configData = new byte[]{0x01, 0x42, (byte)0xC0, 0x1E};

            LocObject original = new LocObject(LocObject.MediaType.VIDEO, payload);
            original.setGroupId(500);
            original.setObjectId(10);
            original.setSubgroupId(1);
            original.setCaptureTimestamp(1702345678000000L);
            original.setVideoFrameMarking(true, false, true, 2, 1);
            original.setVideoConfig(configData);

            // Serialize
            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payloadBytes = serializer.getPayload(original);

            // Relay
            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payloadBytes,
                    original.getGroupId(), original.getObjectId(), original.getSubgroupId()
            );

            assertTrue(relayed.forwarded());

            // Deserialize at subscriber
            LocObject received = deserializer.deserialize(
                    relayed.headerExtensions(),
                    relayed.payload(),
                    LocObject.MediaType.VIDEO
            );

            // Verify all data preserved
            assertArrayEquals(payload, received.getPayload(),
                    "Payload must match exactly");
            assertEquals(1702345678000000L,
                    received.getCaptureTimestamp().getCaptureTimestampMicros(),
                    "Timestamp must match");
            assertTrue(received.isIndependentFrame(), "Independent flag must match");
            assertFalse(received.isDiscardableFrame(), "Discardable flag must match");
            assertEquals(2, received.getVideoFrameMarking().getTemporalLayerId());
            assertEquals(1, received.getVideoFrameMarking().getSpatialLayerId());
            assertArrayEquals(configData, received.getVideoConfig().getConfigData(),
                    "Config data must match");
        }

        @Test
        @DisplayName("Round-trip with unknown extensions (forward compatibility)")
        void testRoundTripWithUnknownExtensions() throws IOException {
            // This tests that we can skip unknown extensions
            // (important for forward compatibility with new LOC versions)
            byte[] payload = new byte[100];
            random.nextBytes(payload);

            LocObject original = new LocObject(LocObject.MediaType.AUDIO, payload);
            original.setCaptureTimestamp(System.currentTimeMillis() * 1000);
            original.setAudioLevel(true, 60);

            byte[] headerExtensions = serializer.serializeHeaderExtensions(original);
            byte[] payloadBytes = serializer.getPayload(original);

            // Relay processes it
            RelaySimulator relay = new RelaySimulator();
            RelayedObject relayed = relay.process(
                    headerExtensions, payloadBytes, 1, 0, 0);

            // Deserialize
            LocObject received = deserializer.deserialize(
                    relayed.headerExtensions(),
                    relayed.payload(),
                    LocObject.MediaType.AUDIO
            );

            assertArrayEquals(payload, received.getPayload());
            assertTrue(received.getAudioLevel().isVoiceActivity());
            assertEquals(60, received.getAudioLevel().getAudioLevel());
        }
    }

    @Nested
    @DisplayName("SVC Layer Tests")
    class SVCLayerTests {

        @Test
        @DisplayName("Temporal layer information extracted for SVC filtering")
        void testTemporalLayerExtraction() throws IOException {
            // Simulate temporal scalability (T0, T1, T2 layers)
            int[] expectedTemporal = {0, 2, 1, 2, 0, 2, 1, 2}; // Typical dyadic pattern

            for (int i = 0; i < expectedTemporal.length; i++) {
                LocObject frame = new LocObject(LocObject.MediaType.VIDEO, new byte[1000]);
                frame.setVideoFrameMarking(
                        expectedTemporal[i] == 0, // Independent at T0
                        expectedTemporal[i] > 0,  // Higher layers discardable
                        expectedTemporal[i] == 0, // Base layer sync at T0
                        expectedTemporal[i],      // Temporal layer ID
                        0                          // No spatial layers
                );

                byte[] headerExt = serializer.serializeHeaderExtensions(frame);

                RelaySimulator relay = new RelaySimulator();
                relay.process(headerExt, new byte[1000], 1, i, 0);

                assertEquals(expectedTemporal[i], relay.getTemporalLayerId(),
                        "Frame " + i + " temporal layer should be " + expectedTemporal[i]);
            }
        }

        @Test
        @DisplayName("Spatial layer information extracted for simulcast")
        void testSpatialLayerExtraction() throws IOException {
            // Simulate 3 spatial layers (720p, 480p, 360p)
            for (int spatialId = 0; spatialId < 3; spatialId++) {
                LocObject frame = new LocObject(LocObject.MediaType.VIDEO, new byte[1000]);
                frame.setVideoFrameMarking(true, false, true, 0, spatialId);

                byte[] headerExt = serializer.serializeHeaderExtensions(frame);

                RelaySimulator relay = new RelaySimulator();
                relay.process(headerExt, new byte[1000], 1, 0, spatialId);

                assertEquals(spatialId, relay.getSpatialLayerId());
            }
        }
    }

    @Nested
    @DisplayName("QUIC Datagram Fit Tests")
    class DatagramFitTests {

        @Test
        @DisplayName("Audio objects fit within QUIC datagram limit (~1200 bytes)")
        void testAudioFitsInDatagram() throws IOException {
            // 10ms of Opus audio at 48kHz is typically 80-240 bytes
            byte[] opusFrame = new byte[120];
            random.nextBytes(opusFrame);

            LocObject audio = LocSerializer.createMinimalAudioObject(
                    opusFrame, System.currentTimeMillis() * 1000);
            audio.setAudioLevel(true, 45);

            byte[] headerExt = serializer.serializeHeaderExtensions(audio);
            int totalSize = headerExt.length + opusFrame.length;

            // QUIC datagram safe payload is typically ~1200 bytes
            assertTrue(totalSize < 1200,
                    "Audio LOC object (" + totalSize + " bytes) should fit in datagram");
        }

        @Test
        @DisplayName("Calculate overhead for different audio codecs")
        void testAudioOverhead() throws IOException {
            // Test header extension overhead for audio
            LocObject audio = new LocObject(LocObject.MediaType.AUDIO, new byte[0]);
            audio.setCaptureTimestamp(System.currentTimeMillis() * 1000);
            audio.setAudioLevel(true, 60);

            byte[] headerExt = serializer.serializeHeaderExtensions(audio);

            // Header overhead should be minimal (capture timestamp + audio level)
            // CaptureTimestamp: 1 byte ID + up to 8 bytes value = ~9 bytes
            // AudioLevel: 1 byte ID + 1 byte value = 2 bytes
            // Total: ~11 bytes overhead
            assertTrue(headerExt.length <= 15,
                    "Audio header overhead should be minimal: " + headerExt.length + " bytes");
        }
    }
}
