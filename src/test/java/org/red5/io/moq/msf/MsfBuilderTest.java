package org.red5.io.moq.msf;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.catalog.MsfCatalog;
import org.red5.io.moq.msf.catalog.MsfCatalogSerializer;
import org.red5.io.moq.msf.catalog.MsfConstants;
import org.red5.io.moq.msf.catalog.MsfTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MSF builder classes and convenience methods.
 */
class MsfBuilderTest {

    // MsfConstants tests

    @Test
    void testMsfVersion() {
        assertEquals(1, MsfConstants.VERSION);
    }

    @Test
    void testCatalogTrackName() {
        assertEquals("catalog", MsfConstants.CATALOG_TRACK_NAME);
    }

    @Test
    void testLatencyThresholds() {
        assertEquals(500, MsfConstants.Latency.REALTIME_MAX_MS);
        assertEquals(500, MsfConstants.Latency.INTERACTIVE_MIN_MS);
        assertEquals(2500, MsfConstants.Latency.INTERACTIVE_MAX_MS);
        assertEquals(2500, MsfConstants.Latency.STANDARD_MIN_MS);
    }

    @Test
    void testGenerateInitialGroupId() {
        long before = System.currentTimeMillis();
        long groupId = MsfConstants.generateInitialGroupId();
        long after = System.currentTimeMillis();
        assertTrue(groupId >= before && groupId <= after);
    }

    // MsfTrack builder tests

    @Test
    void testVideoTrackBuilder() {
        MsfTrack track = MsfTrack.video("1080p-video")
            .namespace("example.com/stream")
            .live()
            .targetLatency(2000)
            .renderGroup(1)
            .codec("av01.0.08M.10.0.110.09")
            .resolution(1920, 1080)
            .framerate(30)
            .bitrate(1500000)
            .build();

        assertEquals("1080p-video", track.getName());
        assertEquals("example.com/stream", track.getNamespace());
        assertEquals("loc", track.getPackaging());
        assertEquals("video", track.getRole());
        assertTrue(track.getIsLive());
        assertEquals(2000L, track.getTargetLatency());
        assertEquals(1, track.getRenderGroup());
        assertEquals(1920, track.getWidth());
        assertEquals(1080, track.getHeight());
        assertEquals(30, track.getFramerate());
        assertEquals(1500000, track.getBitrate());
    }

    @Test
    void testAudioTrackBuilder() {
        MsfTrack track = MsfTrack.audio("audio")
            .live()
            .targetLatency(2000)
            .renderGroup(1)
            .codec("opus")
            .sampleRate(48000)
            .channelConfig("2")
            .bitrate(32000)
            .build();

        assertEquals("audio", track.getName());
        assertEquals("loc", track.getPackaging());
        assertEquals("audio", track.getRole());
        assertEquals(48000, track.getSamplerate());
        assertEquals("2", track.getChannelConfig());
    }

    @Test
    void testCmsfVideoTrackBuilder() {
        byte[] initData = new byte[] {0, 1, 2, 3};
        MsfTrack track = MsfTrack.cmafVideo("hd")
            .live()
            .codec("avc1.640028")
            .initDataBytes(initData)
            .maxGrpSapStartingType(2)
            .maxObjSapStartingType(3)
            .altGroup(1)
            .build();

        assertEquals("hd", track.getName());
        assertEquals("cmaf", track.getPackaging());
        assertEquals("video", track.getRole());
        assertEquals("AAECAw==", track.getInitData());
        assertEquals(2, track.getMaxGrpSapStartingType());
        assertEquals(3, track.getMaxObjSapStartingType());
        assertEquals(1, track.getAltGroup());
    }

    @Test
    void testMediaTimelineTrackBuilder() {
        MsfTrack track = MsfTrack.mediaTimeline("history")
            .live()
            .dependsOn("video")
            .dependsOn("audio")
            .build();

        assertEquals("history", track.getName());
        assertEquals("mediatimeline", track.getPackaging());
        assertEquals("mediatimeline", track.getRole());
        assertEquals("application/json", track.getMimeType());
        assertEquals(List.of("video", "audio"), track.getDepends());
    }

    @Test
    void testEventTimelineTrackBuilder() {
        MsfTrack track = MsfTrack.eventTimeline("scores", "com.sports/live-scores/v1")
            .live()
            .dependsOn("video")
            .build();

        assertEquals("scores", track.getName());
        assertEquals("eventtimeline", track.getPackaging());
        assertEquals("eventtimeline", track.getRole());
        assertEquals("application/json", track.getMimeType());
        assertEquals("com.sports/live-scores/v1", track.getEventType());
        assertEquals(List.of("video"), track.getDepends());
    }

    @Test
    void testCaptionTrackBuilder() {
        MsfTrack track = MsfTrack.caption("cc-en")
            .live()
            .language("en")
            .label("English Captions")
            .build();

        assertEquals("cc-en", track.getName());
        assertEquals("loc", track.getPackaging());
        assertEquals("caption", track.getRole());
        assertEquals("en", track.getLang());
        assertEquals("English Captions", track.getLabel());
    }

    @Test
    void testVodTrackBuilder() {
        MsfTrack track = MsfTrack.video("movie")
            .vod()
            .trackDuration(7200000L) // 2 hours
            .codec("avc1.64001f")
            .resolution(1920, 1080)
            .build();

        assertFalse(track.getIsLive());
        assertEquals(7200000L, track.getTrackDuration());
        assertNull(track.getTargetLatency());
    }

    // MsfCatalog builder tests

    @Test
    void testCatalogBuilder() {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").live().targetLatency(2000).renderGroup(1))
            .addTrack(MsfTrack.audio("audio").live().targetLatency(2000).renderGroup(1))
            .build();

        assertEquals(MsfConstants.VERSION, catalog.getVersion());
        assertNotNull(catalog.getGeneratedAt());
        assertEquals(2, catalog.getTracks().size());
    }

    @Test
    void testCatalogBuilderWithTimestamp() {
        long timestamp = 1746104606044L;
        MsfCatalog catalog = MsfCatalog.builder()
            .generatedAt(timestamp)
            .addTrack(MsfTrack.video("video").live())
            .build();

        assertEquals(timestamp, catalog.getGeneratedAt());
    }

    @Test
    void testTerminationCatalog() {
        MsfCatalog catalog = MsfCatalog.termination();

        assertEquals(MsfConstants.VERSION, catalog.getVersion());
        assertTrue(catalog.getIsComplete());
        assertTrue(catalog.getTracks().isEmpty());
        assertNotNull(catalog.getGeneratedAt());
    }

    @Test
    void testCatalogValidation() {
        // Valid catalog
        MsfCatalog valid = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").live())
            .build();
        assertDoesNotThrow(valid::validate);

        // Invalid: targetLatency on VOD track
        assertThrows(IllegalArgumentException.class, () ->
            MsfCatalog.builder()
                .addTrack(MsfTrack.video("video").vod().targetLatency(2000))
                .build()
        );
    }

    @Test
    void testBuildWithoutValidation() {
        // This would fail validation but we skip it
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").vod().targetLatency(2000))
            .buildWithoutValidation();

        assertNotNull(catalog);
        assertEquals(1, catalog.getTracks().size());
    }

    // Serialization round-trip tests

    @Test
    void testSerializationRoundTrip() throws Exception {
        MsfCatalog original = MsfCatalog.builder()
            .generatedAt(1746104606044L)
            .addTrack(MsfTrack.video("1080p-video")
                .namespace("conference.example.com/alice")
                .live()
                .targetLatency(2000)
                .renderGroup(1)
                .codec("av01.0.08M.10.0.110.09")
                .resolution(1920, 1080)
                .framerate(30)
                .bitrate(1500000))
            .addTrack(MsfTrack.audio("audio")
                .namespace("conference.example.com/alice")
                .live()
                .targetLatency(2000)
                .renderGroup(1)
                .codec("opus")
                .sampleRate(48000)
                .channelConfig("2")
                .bitrate(32000))
            .build();

        MsfCatalogSerializer serializer = new MsfCatalogSerializer();
        String json = serializer.toJson(original);
        MsfCatalog parsed = serializer.fromJsonValidated(json);

        assertEquals(original.getVersion(), parsed.getVersion());
        assertEquals(original.getGeneratedAt(), parsed.getGeneratedAt());
        assertEquals(original.getTracks().size(), parsed.getTracks().size());
        assertEquals("1080p-video", parsed.getTracks().get(0).getName());
        assertEquals(2000L, parsed.getTracks().get(0).getTargetLatency());
    }

    @Test
    void testTimelineTracksCatalog() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").live())
            .addTrack(MsfTrack.audio("audio").live())
            .addTrack(MsfTrack.mediaTimeline("history")
                .live()
                .dependsOn(List.of("video", "audio")))
            .addTrack(MsfTrack.eventTimeline("events", "com.example/events/v1")
                .live()
                .dependsOn("video"))
            .build();

        assertEquals(4, catalog.getTracks().size());
        assertEquals("mediatimeline", catalog.getTracks().get(2).getPackaging());
        assertEquals("eventtimeline", catalog.getTracks().get(3).getPackaging());
    }

    @Test
    void testSimulcastCatalog() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("hd")
                .live()
                .targetLatency(1500)
                .renderGroup(1)
                .altGroup(1)
                .codec("av01")
                .resolution(1920, 1080)
                .bitrate(5000000))
            .addTrack(MsfTrack.video("sd")
                .live()
                .targetLatency(1500)
                .renderGroup(1)
                .altGroup(1)
                .codec("av01")
                .resolution(640, 480)
                .bitrate(500000))
            .addTrack(MsfTrack.audio("audio")
                .live()
                .targetLatency(1500)
                .renderGroup(1)
                .codec("opus"))
            .build();

        assertEquals(3, catalog.getTracks().size());
        // Both video tracks in same alt group with same latency
        assertEquals(catalog.getTracks().get(0).getAltGroup(),
                     catalog.getTracks().get(1).getAltGroup());
        assertEquals(catalog.getTracks().get(0).getTargetLatency(),
                     catalog.getTracks().get(1).getTargetLatency());
    }

    @Test
    void testSvcLayeredCatalog() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("480p15")
                .live()
                .renderGroup(1)
                .temporalId(0)
                .spatialId(0)
                .codec("av01.0.01M.10.0.110.09")
                .resolution(640, 480)
                .framerate(15))
            .addTrack(MsfTrack.video("480p30")
                .live()
                .renderGroup(1)
                .temporalId(1)
                .spatialId(0)
                .codec("av01.0.04M.10.0.110.09")
                .resolution(640, 480)
                .framerate(30)
                .dependsOn("480p15"))
            .addTrack(MsfTrack.video("1080p15")
                .live()
                .renderGroup(1)
                .temporalId(0)
                .spatialId(1)
                .codec("av01.0.05M.10.0.110.09")
                .resolution(1920, 1080)
                .framerate(15)
                .dependsOn("480p15"))
            .addTrack(MsfTrack.audio("audio")
                .live()
                .renderGroup(1)
                .codec("opus"))
            .build();

        assertEquals(4, catalog.getTracks().size());
        assertEquals(List.of("480p15"), catalog.getTracks().get(1).getDepends());
        assertEquals(List.of("480p15"), catalog.getTracks().get(2).getDepends());
    }
}
