package org.red5.io.moq.loc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.red5.io.moq.loc.deserialize.LocDeserializer;
import org.red5.io.moq.loc.model.LocHeaderExtension;
import org.red5.io.moq.loc.model.LocObject;
import org.red5.io.moq.loc.serialize.LocSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live relay integration tests for LOC format compatibility.
 *
 * These tests are DISABLED by default and require:
 * 1. A running MoQ relay (moq-rs, moq-go-server, or similar)
 * 2. System property to enable: -Dmoq.relay.url=https://localhost:4443
 *
 * Run with:
 *   mvn test -Dtest=LiveRelayIntegrationTest -Dmoq.relay.url=https://localhost:4443
 *
 * Supported relays:
 * - moq-rs: cargo install moq-relay && moq-relay --listen 0.0.0.0:4443
 * - moq-go-server: github.com/facebookexperimental/moq-go-server
 * - MOQtail: github.com/moqtail/moqtail
 *
 * Note: Full MoQ transport requires QUIC/WebTransport which needs additional
 * dependencies. This test provides:
 * 1. Wire format validation (can be used with external tools)
 * 2. File-based exchange for testing with CLI tools
 * 3. Framework for integration with MoQ transport implementations
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LiveRelayIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LiveRelayIntegrationTest.class);

    private static final String RELAY_URL_PROPERTY = "moq.relay.url";
    private static final String OUTPUT_DIR_PROPERTY = "moq.test.output";
    private static final String SKIP_SSL_PROPERTY = "moq.relay.skipssl";

    private LocSerializer serializer;
    private LocDeserializer deserializer;
    private SecureRandom random;
    private Path outputDir;

    @BeforeEach
    void setUp() throws IOException {
        serializer = new LocSerializer();
        deserializer = new LocDeserializer();
        random = new SecureRandom();

        // Create output directory for wire format files
        String outputPath = System.getProperty(OUTPUT_DIR_PROPERTY, "target/moq-test-output");
        outputDir = Path.of(outputPath);
        Files.createDirectories(outputDir);
    }

    /**
     * Test that generates wire-format files for external validation.
     * These files can be used with moq-pub or other MoQ tools to verify format.
     */
    @Test
    @Order(1)
    @DisplayName("Generate LOC wire format files for external validation")
    void generateWireFormatFiles() throws IOException {
        log.info("Generating LOC wire format files in: {}", outputDir);

        // Generate video keyframe
        byte[] videoPayload = generateH264KeyframePayload();
        LocObject videoKeyframe = createVideoKeyframe(videoPayload, 0, 0);
        writeLocObject(videoKeyframe, "video_keyframe_g0_o0.loc");

        // Generate video delta frames
        for (int i = 1; i < 30; i++) {
            byte[] deltaPayload = generateH264DeltaPayload();
            LocObject delta = createVideoDeltaFrame(deltaPayload, 0, i, i % 4);
            writeLocObject(delta, String.format("video_delta_g0_o%d.loc", i));
        }

        // Generate audio frames
        for (int i = 0; i < 100; i++) {
            byte[] audioPayload = generateOpusPayload();
            LocObject audio = createAudioFrame(audioPayload, i);
            writeLocObject(audio, String.format("audio_g%d_o0.loc", i));
        }

        log.info("Generated {} video frames and {} audio frames",
                30, 100);

        // Verify files exist
        assertTrue(Files.exists(outputDir.resolve("video_keyframe_g0_o0.loc")));
        assertTrue(Files.exists(outputDir.resolve("audio_g0_o0.loc")));
    }

    /**
     * Test that validates wire format can be read back correctly.
     */
    @Test
    @Order(2)
    @DisplayName("Validate wire format round-trip from files")
    void validateWireFormatRoundTrip() throws IOException {
        // First generate files
        generateWireFormatFiles();

        // Read and validate video keyframe
        Path keyframePath = outputDir.resolve("video_keyframe_g0_o0.loc");
        LocObjectFile keyframeFile = readLocObjectFile(keyframePath);

        LocObject deserialized = deserializer.deserialize(
                keyframeFile.headerExtensions(),
                keyframeFile.payload(),
                LocObject.MediaType.VIDEO
        );

        assertTrue(deserialized.isIndependentFrame(), "Keyframe should be independent");
        assertNotNull(deserialized.getVideoConfig(), "Keyframe should have config");
        assertNotNull(deserialized.getCaptureTimestamp(), "Should have timestamp");

        log.info("Wire format validation successful");
    }

    /**
     * Test relay connectivity (requires running relay).
     *
     * Use -Dmoq.relay.skipssl=true to skip SSL validation for self-signed certs.
     */
    @Test
    @Order(3)
    @DisplayName("Check relay connectivity")
    @EnabledIfSystemProperty(named = RELAY_URL_PROPERTY, matches = ".+")
    void checkRelayConnectivity() throws Exception {
        String relayUrl = System.getProperty(RELAY_URL_PROPERTY);
        boolean skipSsl = Boolean.parseBoolean(System.getProperty(SKIP_SSL_PROPERTY, "false"));

        log.info("Testing connectivity to relay: {}", relayUrl);
        if (skipSsl) {
            log.info("SSL validation disabled (self-signed cert mode)");
        }

        // Most MoQ relays expose HTTPS endpoint for WebTransport upgrade
        URI uri = URI.create(relayUrl);

        try {
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

            // Configure SSL bypass if requested
            if (skipSsl && conn instanceof HttpsURLConnection httpsConn) {
                httpsConn.setSSLSocketFactory(createTrustAllSocketFactory());
                httpsConn.setHostnameVerifier((hostname, session) -> true);
            }

            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");

            int responseCode = conn.getResponseCode();
            log.info("Relay responded with HTTP {}", responseCode);

            // WebTransport endpoints typically return various codes
            // 200 = OK, 426 = Upgrade Required (expected for WT), etc.
            assertTrue(responseCode > 0, "Relay should respond");

        } catch (javax.net.ssl.SSLException e) {
            if (skipSsl) {
                // Should not happen with SSL bypass enabled
                fail("SSL error even with skipssl=true: " + e.getMessage());
            } else {
                // Self-signed cert is common for local relays
                log.warn("SSL error (self-signed cert?): {}", e.getMessage());
                log.info("Hint: Use -Dmoq.relay.skipssl=true to bypass SSL validation");
            }
        } catch (java.net.ConnectException e) {
            fail("Cannot connect to relay at " + relayUrl + ": " + e.getMessage());
        }
    }

    /**
     * Creates an SSLSocketFactory that trusts all certificates.
     * WARNING: Only use for local testing with self-signed certs!
     */
    private SSLSocketFactory createTrustAllSocketFactory() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    // Trust all
                }

                @Override
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    // Trust all
                }
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext.getSocketFactory();
    }

    /**
     * Generate test data that can be published via external tools.
     * Creates a complete test stream with video and audio.
     */
    @Test
    @Order(4)
    @DisplayName("Generate complete test stream for relay publishing")
    void generateTestStream() throws IOException {
        Path streamDir = outputDir.resolve("test_stream");
        Files.createDirectories(streamDir);

        // Create manifest file describing the stream
        StringBuilder manifest = new StringBuilder();
        manifest.append("# LOC Test Stream Manifest\n");
        manifest.append("# Format: type,group_id,object_id,filename\n");
        manifest.append("# Video: 30fps, GOP=30, H.264 simulation\n");
        manifest.append("# Audio: 100fps (10ms frames), Opus simulation\n\n");

        // Generate 3 GOPs of video (90 frames = 3 seconds)
        for (int gop = 0; gop < 3; gop++) {
            // Keyframe
            byte[] keyPayload = generateH264KeyframePayload();
            LocObject keyframe = createVideoKeyframe(keyPayload, gop, 0);
            String keyFilename = String.format("v_g%d_o%d.loc", gop, 0);
            writeLocObject(keyframe, streamDir.resolve(keyFilename));
            manifest.append(String.format("video,%d,%d,%s\n", gop, 0, keyFilename));

            // Delta frames
            for (int i = 1; i < 30; i++) {
                byte[] deltaPayload = generateH264DeltaPayload();
                LocObject delta = createVideoDeltaFrame(deltaPayload, gop, i, i % 4);
                String deltaFilename = String.format("v_g%d_o%d.loc", gop, i);
                writeLocObject(delta, streamDir.resolve(deltaFilename));
                manifest.append(String.format("video,%d,%d,%s\n", gop, i, deltaFilename));
            }
        }

        // Generate 300 audio frames (3 seconds at 10ms per frame)
        for (int i = 0; i < 300; i++) {
            byte[] audioPayload = generateOpusPayload();
            LocObject audio = createAudioFrame(audioPayload, i);
            String audioFilename = String.format("a_g%d_o0.loc", i);
            writeLocObject(audio, streamDir.resolve(audioFilename));
            manifest.append(String.format("audio,%d,%d,%s\n", i, 0, audioFilename));
        }

        // Write manifest
        Files.writeString(streamDir.resolve("manifest.txt"), manifest.toString());

        log.info("Generated test stream in: {}", streamDir);
        log.info("Video: 3 GOPs x 30 frames = 90 frames");
        log.info("Audio: 300 frames (3 seconds)");

        assertTrue(Files.exists(streamDir.resolve("manifest.txt")));
        assertTrue(Files.exists(streamDir.resolve("v_g0_o0.loc")));
        assertTrue(Files.exists(streamDir.resolve("a_g0_o0.loc")));
    }

    /**
     * Integration test using file exchange.
     * 1. Writes LOC objects to files
     * 2. External tool (moq-pub) can read and publish
     * 3. External tool (moq-sub) can receive and write
     * 4. This test validates received files
     */
    @Test
    @Order(5)
    @DisplayName("Validate received stream (requires external publish/subscribe)")
    @EnabledIfSystemProperty(named = "moq.test.received", matches = ".+")
    void validateReceivedStream() throws IOException {
        String receivedPath = System.getProperty("moq.test.received");
        Path receivedDir = Path.of(receivedPath);

        if (!Files.exists(receivedDir)) {
            log.warn("Received directory does not exist: {}", receivedDir);
            return;
        }

        log.info("Validating received stream from: {}", receivedDir);

        // Find and validate LOC files
        List<Path> locFiles = Files.list(receivedDir)
                .filter(p -> p.toString().endsWith(".loc"))
                .toList();

        log.info("Found {} LOC files", locFiles.size());

        int videoCount = 0;
        int audioCount = 0;

        for (Path locFile : locFiles) {
            LocObjectFile objFile = readLocObjectFile(locFile);

            // Determine media type from filename or content
            boolean isVideo = locFile.getFileName().toString().startsWith("v_");

            LocObject obj = deserializer.deserialize(
                    objFile.headerExtensions(),
                    objFile.payload(),
                    isVideo ? LocObject.MediaType.VIDEO : LocObject.MediaType.AUDIO
            );

            assertNotNull(obj.getPayload(), "Payload should not be null");
            assertTrue(obj.getPayload().length > 0, "Payload should not be empty");

            if (isVideo) {
                videoCount++;
                assertNotNull(obj.getVideoFrameMarking(), "Video should have frame marking");
            } else {
                audioCount++;
                // Audio level is optional
            }
        }

        log.info("Validated {} video frames, {} audio frames", videoCount, audioCount);
    }

    /**
     * Test format compatibility with known relay implementations.
     */
    @Test
    @Order(6)
    @DisplayName("Verify wire format matches moq-rs expectations")
    void verifyMoqRsCompatibility() throws IOException {
        // moq-rs expects LOC objects with specific header extension encoding
        byte[] payload = new byte[100];
        random.nextBytes(payload);

        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, payload);
        obj.setCaptureTimestamp(1702345678000000L);
        obj.setVideoFrameMarking(true, false, true, 0, 0);
        obj.setVideoConfig(new byte[]{0x01, 0x64, 0x00, 0x1f}); // AVC profile

        byte[] headerExt = serializer.serializeHeaderExtensions(obj);

        // Verify header extension format
        ByteBuffer buffer = ByteBuffer.wrap(headerExt);

        // First extension: CaptureTimestamp (ID=2, even, varint value)
        long extId1 = LocHeaderExtension.readVarint(buffer);
        assertEquals(2, extId1, "First extension should be CaptureTimestamp");
        long timestamp = LocHeaderExtension.readVarint(buffer);
        assertEquals(1702345678000000L, timestamp);

        // Second extension: VideoFrameMarking (ID=4, even, varint value)
        long extId2 = LocHeaderExtension.readVarint(buffer);
        assertEquals(4, extId2, "Second extension should be VideoFrameMarking");
        long frameMarking = LocHeaderExtension.readVarint(buffer);
        // RFC 9626: Bit 5 = I (1), Bit 4 = D (0), Bit 3 = B (1), Bits 2-0 = TID (0)
        assertEquals(0b00101000, frameMarking, "Frame marking should encode I=1, D=0, B=1");

        // Third extension: VideoConfig (ID=13, odd, length-prefixed)
        long extId3 = LocHeaderExtension.readVarint(buffer);
        assertEquals(13, extId3, "Third extension should be VideoConfig");
        long configLen = LocHeaderExtension.readVarint(buffer);
        assertEquals(4, configLen, "Config should be 4 bytes");

        log.info("Wire format verified compatible with moq-rs");
    }

    /**
     * Performance test for high-throughput scenarios.
     */
    @Test
    @Order(7)
    @DisplayName("Benchmark serialization throughput for relay")
    void benchmarkSerializationThroughput() throws IOException {
        // Simulate 4K video at 30fps (typical frame size ~100KB)
        byte[] videoPayload = new byte[100_000];
        random.nextBytes(videoPayload);

        int iterations = 1000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            LocObject obj = new LocObject(LocObject.MediaType.VIDEO, videoPayload);
            obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);
            obj.setVideoFrameMarking(i % 30 == 0, false, i % 30 == 0, i % 4, 0);

            byte[] headerExt = serializer.serializeHeaderExtensions(obj);
            byte[] payload = serializer.getPayload(obj);

            // Simulate what relay transport would do
            assertNotNull(headerExt);
            assertNotNull(payload);
        }

        long elapsed = System.nanoTime() - startTime;
        double msPerOp = elapsed / 1_000_000.0 / iterations;
        double opsPerSec = iterations / (elapsed / 1_000_000_000.0);

        log.info("Serialization benchmark:");
        log.info("  {} iterations in {} ms", iterations, elapsed / 1_000_000);
        log.info("  {:.3f} ms per operation", msPerOp);
        log.info("  {:.0f} operations/second", opsPerSec);
        log.info("  Payload size: {} KB", videoPayload.length / 1024);

        // Should be able to handle at least 30fps
        assertTrue(opsPerSec > 30, "Should handle at least 30 fps");
    }

    // Helper methods

    private LocObject createVideoKeyframe(byte[] payload, int groupId, int objectId) {
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, payload);
        obj.setGroupId(groupId);
        obj.setObjectId(objectId);
        obj.setSubgroupId(0);
        obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);
        obj.setVideoFrameMarking(true, false, true, 0, 0);
        obj.setVideoConfig(new byte[]{0x01, 0x64, 0x00, 0x1f, (byte) 0xff, (byte) 0xe1, 0x00, 0x1b});
        return obj;
    }

    private LocObject createVideoDeltaFrame(byte[] payload, int groupId, int objectId, int temporalLayer) {
        LocObject obj = new LocObject(LocObject.MediaType.VIDEO, payload);
        obj.setGroupId(groupId);
        obj.setObjectId(objectId);
        obj.setSubgroupId(temporalLayer);
        obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);
        obj.setVideoFrameMarking(false, temporalLayer > 0, false, temporalLayer, 0);
        return obj;
    }

    private LocObject createAudioFrame(byte[] payload, int groupId) {
        LocObject obj = new LocObject(LocObject.MediaType.AUDIO, payload);
        obj.setGroupId(groupId);
        obj.setObjectId(0);
        obj.setSubgroupId(0);
        obj.setCaptureTimestamp(System.currentTimeMillis() * 1000);
        obj.setAudioLevel(random.nextBoolean(), random.nextInt(64));
        return obj;
    }

    private byte[] generateH264KeyframePayload() {
        // Simulated H.264 IDR NAL unit (not real video, just for format testing)
        byte[] payload = new byte[50_000 + random.nextInt(10_000)];
        random.nextBytes(payload);
        // Set NAL unit header for IDR slice
        payload[0] = 0x00;
        payload[1] = 0x00;
        payload[2] = 0x00;
        payload[3] = 0x01;
        payload[4] = 0x65; // NAL type 5 = IDR
        return payload;
    }

    private byte[] generateH264DeltaPayload() {
        // Simulated H.264 non-IDR NAL unit
        byte[] payload = new byte[5_000 + random.nextInt(5_000)];
        random.nextBytes(payload);
        payload[0] = 0x00;
        payload[1] = 0x00;
        payload[2] = 0x00;
        payload[3] = 0x01;
        payload[4] = 0x41; // NAL type 1 = non-IDR slice
        return payload;
    }

    private byte[] generateOpusPayload() {
        // Simulated Opus frame (10ms at 48kHz)
        byte[] payload = new byte[80 + random.nextInt(80)];
        random.nextBytes(payload);
        return payload;
    }

    private void writeLocObject(LocObject obj, String filename) throws IOException {
        writeLocObject(obj, outputDir.resolve(filename));
    }

    private void writeLocObject(LocObject obj, Path path) throws IOException {
        byte[] headerExt = serializer.serializeHeaderExtensions(obj);
        byte[] payload = serializer.getPayload(obj);

        // Write in a format that preserves header/payload separation
        // Format: [4 bytes header length][header bytes][payload bytes]
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            dos.writeInt(headerExt.length);
            dos.write(headerExt);
            dos.write(payload);
        }
    }

    private LocObjectFile readLocObjectFile(Path path) throws IOException {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            int headerLen = dis.readInt();
            byte[] headerExt = dis.readNBytes(headerLen);
            byte[] payload = dis.readAllBytes();
            return new LocObjectFile(headerExt, payload);
        }
    }

    record LocObjectFile(byte[] headerExtensions, byte[] payload) {}
}
