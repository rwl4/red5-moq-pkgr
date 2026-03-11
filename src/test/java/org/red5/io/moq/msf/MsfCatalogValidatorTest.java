package org.red5.io.moq.msf;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.catalog.MsfCatalogValidator;
import org.red5.io.moq.msf.catalog.PackagingType;
import org.red5.io.moq.msf.catalog.TrackRole;
import org.red5.io.moq.warp.catalog.WarpCatalog;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MsfCatalogValidatorTest {

    // TrackRole enum tests

    @Test
    void testTrackRoleValues() {
        assertEquals("audiodescription", TrackRole.AUDIO_DESCRIPTION.getValue());
        assertEquals("video", TrackRole.VIDEO.getValue());
        assertEquals("audio", TrackRole.AUDIO.getValue());
        assertEquals("mediatimeline", TrackRole.MEDIA_TIMELINE.getValue());
        assertEquals("eventtimeline", TrackRole.EVENT_TIMELINE.getValue());
        assertEquals("caption", TrackRole.CAPTION.getValue());
        assertEquals("subtitle", TrackRole.SUBTITLE.getValue());
        assertEquals("signlanguage", TrackRole.SIGN_LANGUAGE.getValue());
    }

    @Test
    void testTrackRoleIsPredefined() {
        assertTrue(TrackRole.isPredefined("video"));
        assertTrue(TrackRole.isPredefined("audio"));
        assertTrue(TrackRole.isPredefined("mediatimeline"));
        assertFalse(TrackRole.isPredefined("custom-role"));
        assertFalse(TrackRole.isPredefined(null));
    }

    @Test
    void testTrackRoleFromValue() {
        assertEquals(TrackRole.VIDEO, TrackRole.fromValue("video"));
        assertEquals(TrackRole.CAPTION, TrackRole.fromValue("caption"));
        assertNull(TrackRole.fromValue("custom-role"));
        assertNull(TrackRole.fromValue(null));
    }

    @Test
    void testTrackRoleAllValues() {
        var allValues = TrackRole.allValues();
        assertEquals(8, allValues.size());
        assertTrue(allValues.contains("video"));
        assertTrue(allValues.contains("audiodescription"));
    }

    // PackagingType enum tests

    @Test
    void testPackagingTypeValues() {
        assertEquals("loc", PackagingType.LOC.getValue());
        assertEquals("cmaf", PackagingType.CMAF.getValue());
        assertEquals("mediatimeline", PackagingType.MEDIA_TIMELINE.getValue());
        assertEquals("eventtimeline", PackagingType.EVENT_TIMELINE.getValue());
    }

    @Test
    void testPackagingTypeIsValid() {
        assertTrue(PackagingType.isValid("loc"));
        assertTrue(PackagingType.isValid("cmaf"));
        assertTrue(PackagingType.isValid("mediatimeline"));
        assertTrue(PackagingType.isValid("eventtimeline"));
        assertFalse(PackagingType.isValid("timeline")); // WARP value, not MSF
        assertFalse(PackagingType.isValid(null));
    }

    @Test
    void testPackagingTypeFromValue() {
        assertEquals(PackagingType.LOC, PackagingType.fromValue("loc"));
        assertEquals(PackagingType.CMAF, PackagingType.fromValue("cmaf"));
        assertEquals(PackagingType.MEDIA_TIMELINE, PackagingType.fromValue("mediatimeline"));
        assertNull(PackagingType.fromValue(null));
    }

    // MsfCatalogValidator tests

    @Test
    void testValidLocTrack() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("loc");
        track.setIsLive(true);
        track.setTargetLatency(2000L);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testTargetLatencyNotAllowedForVod() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("loc");
        track.setIsLive(false);
        track.setTargetLatency(2000L); // Invalid for VOD

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testTrackDurationNotAllowedForLive() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("loc");
        track.setIsLive(true);
        track.setTrackDuration(60000L); // Invalid for live

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testMediaTimelineTrackRequirements() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("history");
        timeline.setPackaging("mediatimeline");
        timeline.setDepends(List.of("video"));
        timeline.setMimeType("application/json");
        timeline.setIsLive(true);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testMediaTimelineRequiresDepends() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("history");
        timeline.setPackaging("mediatimeline");
        timeline.setMimeType("application/json");
        timeline.setIsLive(true);
        // Missing depends

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testMediaTimelineRequiresJsonMimeType() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("history");
        timeline.setPackaging("mediatimeline");
        timeline.setDepends(List.of("video"));
        timeline.setMimeType("text/csv"); // Wrong mimeType
        timeline.setIsLive(true);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testEventTimelineTrackRequirements() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("events");
        timeline.setPackaging("eventtimeline");
        timeline.setDepends(List.of("video"));
        timeline.setMimeType("application/json");
        timeline.setEventType("com.example/sports-scores");
        timeline.setIsLive(true);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testValidCmsfTrack() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("cmaf");
        track.setIsLive(true);
        track.setInitData("AAAA");
        track.setMaxGrpSapStartingType(2);
        track.setMaxObjSapStartingType(3);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testCmsfTrackRequiresInitData() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("cmaf");
        track.setIsLive(true);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testCmsfTrackRejectsInvalidGroupSapType() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("cmaf");
        track.setIsLive(true);
        track.setInitData("AAAA");
        track.setMaxGrpSapStartingType(3);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testCmsfTrackRejectsInvalidObjectSapType() {
        WarpTrack track = new WarpTrack();
        track.setName("video");
        track.setPackaging("cmaf");
        track.setIsLive(true);
        track.setInitData("AAAA");
        track.setMaxObjSapStartingType(4);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(track));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testEventTimelineRequiresEventType() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("events");
        timeline.setPackaging("eventtimeline");
        timeline.setDepends(List.of("video"));
        timeline.setMimeType("application/json");
        timeline.setIsLive(true);
        // Missing eventType

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testRenderGroupMustHaveSameLatency() {
        WarpTrack video = new WarpTrack();
        video.setName("video");
        video.setPackaging("loc");
        video.setIsLive(true);
        video.setTargetLatency(2000L);
        video.setRenderGroup(1);

        WarpTrack audio = new WarpTrack();
        audio.setName("audio");
        audio.setPackaging("loc");
        audio.setIsLive(true);
        audio.setTargetLatency(3000L); // Different latency - invalid
        audio.setRenderGroup(1);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(video, audio));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testRenderGroupWithSameLatencyValid() {
        WarpTrack video = new WarpTrack();
        video.setName("video");
        video.setPackaging("loc");
        video.setIsLive(true);
        video.setTargetLatency(2000L);
        video.setRenderGroup(1);

        WarpTrack audio = new WarpTrack();
        audio.setName("audio");
        audio.setPackaging("loc");
        audio.setIsLive(true);
        audio.setTargetLatency(2000L); // Same latency - valid
        audio.setRenderGroup(1);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(video, audio));

        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testAltGroupMustHaveSameLatency() {
        WarpTrack hd = new WarpTrack();
        hd.setName("hd");
        hd.setPackaging("loc");
        hd.setIsLive(true);
        hd.setTargetLatency(1500L);
        hd.setAltGroup(1);

        WarpTrack sd = new WarpTrack();
        sd.setName("sd");
        sd.setPackaging("loc");
        sd.setIsLive(true);
        sd.setTargetLatency(2500L); // Different latency - invalid
        sd.setAltGroup(1);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(hd, sd));

        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testIsCompleteCatalogTermination() {
        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setIsComplete(true);
        catalog.setTracks(Collections.emptyList());

        // Empty tracks with isComplete=true is valid for termination
        assertDoesNotThrow(() -> MsfCatalogValidator.validateCatalog(catalog));
    }

    @Test
    void testWarpTimelinePackagingRejectedByMsf() {
        WarpTrack timeline = new WarpTrack();
        timeline.setName("timeline");
        timeline.setPackaging("timeline"); // WARP value, not MSF
        timeline.setType("timeline");
        timeline.setDepends(List.of("video"));
        timeline.setMimeType("text/csv");
        timeline.setIsLive(true);

        WarpCatalog catalog = new WarpCatalog();
        catalog.setVersion(1);
        catalog.setTracks(List.of(timeline));

        // MSF does not allow "timeline" packaging, only "mediatimeline"
        assertThrows(IllegalArgumentException.class,
            () -> MsfCatalogValidator.validateCatalog(catalog));
    }
}
