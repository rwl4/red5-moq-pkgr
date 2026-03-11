package org.red5.io.moq.msf;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.catalog.MsfCatalog;
import org.red5.io.moq.msf.catalog.MsfCatalogSerializer;
import org.red5.io.moq.msf.catalog.MsfTrack;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MsfCatalogSerializer including edge cases and error handling.
 */
class MsfSerializerTest {

    private final MsfCatalogSerializer serializer = new MsfCatalogSerializer();

    // Basic serialization tests

    @Test
    void testSerializeEmptyTracks() throws Exception {
        MsfCatalog catalog = MsfCatalog.termination();
        String json = serializer.toJson(catalog);

        assertTrue(json.contains("\"isComplete\":true") || json.contains("\"isComplete\": true"));
        assertTrue(json.contains("\"tracks\":[]") || json.contains("\"tracks\": []"));
    }

    @Test
    void testDeserializeMinimalCatalog() throws Exception {
        String json = "{\"version\":1,\"tracks\":[{\"name\":\"v\",\"packaging\":\"loc\",\"isLive\":true}]}";
        MsfCatalog catalog = serializer.fromJson(json);

        assertEquals(1, catalog.getVersion());
        assertEquals(1, catalog.getTracks().size());
        assertEquals("v", catalog.getTracks().get(0).getName());
    }

    @Test
    void testDeserializeWithAllFields() throws Exception {
        String json = """
            {
              "version": 1,
              "generatedAt": 1746104606044,
              "isComplete": false,
              "tracks": [
                {
                  "name": "video",
                  "namespace": "example.com/stream",
                  "packaging": "loc",
                  "role": "video",
                  "isLive": true,
                  "targetLatency": 2000,
                  "renderGroup": 1,
                  "altGroup": 1,
                  "codec": "av01",
                  "mimeType": "video/av1",
                  "framerate": 30,
                  "timescale": 90000,
                  "bitrate": 1500000,
                  "width": 1920,
                  "height": 1080,
                  "displayWidth": 1920,
                  "displayHeight": 1080,
                  "lang": "en",
                  "label": "Main Video",
                  "temporalId": 0,
                  "spatialId": 0,
                  "initData": "AAAA"
                }
              ]
            }
            """;

        MsfCatalog catalog = serializer.fromJson(json);
        WarpTrack track = catalog.getTracks().get(0);

        assertEquals("video", track.getName());
        assertEquals("example.com/stream", track.getNamespace());
        assertEquals("loc", track.getPackaging());
        assertEquals("video", track.getRole());
        assertTrue(track.getIsLive());
        assertEquals(2000L, track.getTargetLatency());
        assertEquals(1, track.getRenderGroup());
        assertEquals(1, track.getAltGroup());
        assertEquals("av01", track.getCodec());
        assertEquals(30, track.getFramerate());
        assertEquals(1920, track.getWidth());
        assertEquals(1080, track.getHeight());
        assertEquals("en", track.getLang());
        assertEquals("Main Video", track.getLabel());
        assertEquals(0, track.getTemporalId());
        assertEquals(0, track.getSpatialId());
        assertEquals("AAAA", track.getInitData());
    }

    // Delta update serialization tests

    @Test
    void testSerializeDeltaAddTracks() throws Exception {
        MsfCatalog delta = new MsfCatalog();
        delta.setDeltaUpdate(true);
        delta.setGeneratedAt(System.currentTimeMillis());

        WarpTrack newTrack = new WarpTrack();
        newTrack.setName("audio");
        newTrack.setPackaging("loc");
        newTrack.setIsLive(true);
        delta.setAddTracks(List.of(newTrack));

        String json = serializer.toJson(delta);
        MsfCatalog parsed = serializer.fromJson(json);

        assertTrue(parsed.getDeltaUpdate());
        assertEquals(1, parsed.getAddTracks().size());
        assertEquals("audio", parsed.getAddTracks().get(0).getName());
    }

    @Test
    void testSerializeDeltaRemoveTracks() throws Exception {
        MsfCatalog delta = new MsfCatalog();
        delta.setDeltaUpdate(true);

        WarpTrack remove = new WarpTrack();
        remove.setName("video");
        delta.setRemoveTracks(List.of(remove));

        String json = serializer.toJson(delta);
        MsfCatalog parsed = serializer.fromJson(json);

        assertTrue(parsed.getDeltaUpdate());
        assertEquals(1, parsed.getRemoveTracks().size());
    }

    @Test
    void testSerializeDeltaCloneTracks() throws Exception {
        MsfCatalog delta = new MsfCatalog();
        delta.setDeltaUpdate(true);

        WarpTrack clone = new WarpTrack();
        clone.setName("video-720p");
        clone.setParentName("video-1080p");
        clone.setWidth(1280);
        clone.setHeight(720);
        clone.setPackaging("loc");
        clone.setIsLive(true);
        delta.setCloneTracks(List.of(clone));

        String json = serializer.toJson(delta);
        MsfCatalog parsed = serializer.fromJson(json);

        assertTrue(parsed.getDeltaUpdate());
        assertEquals(1, parsed.getCloneTracks().size());
        assertEquals("video-1080p", parsed.getCloneTracks().get(0).getParentName());
    }

    // Validation integration tests

    @Test
    void testFromJsonValidatedSuccess() throws Exception {
        String json = "{\"version\":1,\"tracks\":[{\"name\":\"v\",\"packaging\":\"loc\",\"isLive\":true}]}";
        MsfCatalog catalog = serializer.fromJsonValidated(json);
        assertNotNull(catalog);
    }

    @Test
    void testFromJsonValidatedFailure() {
        // Invalid: targetLatency on VOD track
        String json = "{\"version\":1,\"tracks\":[{\"name\":\"v\",\"packaging\":\"loc\",\"isLive\":false,\"targetLatency\":2000}]}";

        assertThrows(IllegalArgumentException.class, () -> serializer.fromJsonValidated(json));
    }

    @Test
    void testFromJsonInvalidJson() {
        String invalidJson = "{ not valid json }";
        assertThrows(IOException.class, () -> serializer.fromJson(invalidJson));
    }

    @Test
    void testFromJsonEmptyString() {
        // Empty string may return null or throw exception depending on implementation
        assertThrows(Exception.class, () -> serializer.fromJson(""));
    }

    @Test
    void testFromJsonNullString() {
        assertThrows(Exception.class, () -> serializer.fromJson(null));
    }

    // Track dependencies serialization

    @Test
    void testSerializeTrackDependencies() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("base").live().temporalId(0))
            .addTrack(MsfTrack.video("enhanced").live().temporalId(1).dependsOn("base"))
            .buildWithoutValidation();

        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJson(json);

        assertEquals(2, parsed.getTracks().size());
        assertNull(parsed.getTracks().get(0).getDepends());
        assertEquals(List.of("base"), parsed.getTracks().get(1).getDepends());
    }

    // Audio track specific fields

    @Test
    void testSerializeAudioTrack() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.audio("audio")
                .live()
                .codec("opus")
                .sampleRate(48000)
                .channelConfig("2")
                .bitrate(128000))
            .buildWithoutValidation();

        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJson(json);

        WarpTrack audio = parsed.getTracks().get(0);
        assertEquals(48000, audio.getSamplerate());
        assertEquals("2", audio.getChannelConfig());
        assertEquals(128000, audio.getBitrate());
    }

    @Test
    void testSerializeCmsfTrack() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.cmafVideo("video")
                .live()
                .initData("AAAA")
                .codec("avc1.640028")
                .maxGrpSapStartingType(2)
                .maxObjSapStartingType(3))
            .build();

        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJsonValidated(json);

        WarpTrack track = parsed.getTracks().get(0);
        assertEquals("cmaf", track.getPackaging());
        assertEquals("AAAA", track.getInitData());
        assertEquals(2, track.getMaxGrpSapStartingType());
        assertEquals(3, track.getMaxObjSapStartingType());
    }

    // Timeline track serialization

    @Test
    void testSerializeMediaTimelineTrack() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").live())
            .addTrack(MsfTrack.mediaTimeline("history")
                .live()
                .dependsOn("video"))
            .build();

        String json = serializer.toJson(catalog);
        assertTrue(json.contains("\"packaging\":\"mediatimeline\"") ||
                   json.contains("\"packaging\": \"mediatimeline\""));

        MsfCatalog parsed = serializer.fromJson(json);
        assertEquals("mediatimeline", parsed.getTracks().get(1).getPackaging());
        assertEquals("application/json", parsed.getTracks().get(1).getMimeType());
    }

    @Test
    void testSerializeEventTimelineTrack() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("video").live())
            .addTrack(MsfTrack.eventTimeline("events", "com.example/scores/v1")
                .live()
                .dependsOn("video"))
            .build();

        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJson(json);

        WarpTrack eventTrack = parsed.getTracks().get(1);
        assertEquals("eventtimeline", eventTrack.getPackaging());
        assertEquals("com.example/scores/v1", eventTrack.getEventType());
    }

    // VOD catalog serialization

    @Test
    void testSerializeVodCatalog() throws Exception {
        MsfCatalog catalog = MsfCatalog.builder()
            .addTrack(MsfTrack.video("movie")
                .vod()
                .trackDuration(7200000L)
                .codec("avc1.64001f")
                .resolution(1920, 1080))
            .build();

        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJson(json);

        WarpTrack track = parsed.getTracks().get(0);
        assertFalse(track.getIsLive());
        assertEquals(7200000L, track.getTrackDuration());
    }

    // Large catalog handling

    @Test
    void testSerializeLargeCatalog() throws Exception {
        MsfCatalog.Builder builder = MsfCatalog.builder();

        // Add 100 tracks
        for (int i = 0; i < 100; i++) {
            builder.addTrack(MsfTrack.video("video-" + i)
                .live()
                .renderGroup(i % 10)
                .resolution(1920, 1080)
                .bitrate(1000000 + i * 10000));
        }

        MsfCatalog catalog = builder.buildWithoutValidation();
        String json = serializer.toJson(catalog);
        MsfCatalog parsed = serializer.fromJson(json);

        assertEquals(100, parsed.getTracks().size());
        assertEquals("video-0", parsed.getTracks().get(0).getName());
        assertEquals("video-99", parsed.getTracks().get(99).getName());
    }

    // Null field handling

    @Test
    void testNullFieldsNotSerialized() throws Exception {
        MsfTrack track = MsfTrack.video("simple").live().build();
        MsfCatalog catalog = new MsfCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        String json = serializer.toJson(catalog);

        // Null fields should not appear in JSON
        assertFalse(json.contains("\"targetLatency\":null"));
        assertFalse(json.contains("\"trackDuration\":null"));
        assertFalse(json.contains("\"altGroup\":null"));
    }
}
