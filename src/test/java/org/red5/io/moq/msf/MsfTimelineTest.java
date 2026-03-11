package org.red5.io.moq.msf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.timeline.MsfEventTimeline;
import org.red5.io.moq.msf.timeline.MsfEventTimelineEntry;
import org.red5.io.moq.msf.timeline.MsfMediaTimeline;
import org.red5.io.moq.msf.timeline.MsfMediaTimelineRecord;

import com.google.gson.JsonObject;

class MsfTimelineTest {

    @Test
    void testMediaTimelineRoundTrip() throws Exception {
        MsfMediaTimeline timeline = new MsfMediaTimeline();
        List<MsfMediaTimelineRecord> records = List.of(
            new MsfMediaTimelineRecord(0, 0, 0, 1759924158381L),
            new MsfMediaTimelineRecord(2002, 1, 0, 1759924160383L),
            new MsfMediaTimelineRecord(4004, 2, 0, 1759924162385L)
        );

        String json = timeline.toJson(records);
        List<MsfMediaTimelineRecord> parsed = timeline.fromJson(json);

        assertEquals(3, parsed.size());
        assertEquals(0, parsed.get(0).getMediaPtsMillis());
        assertEquals(0, parsed.get(0).getGroupId());
        assertEquals(0, parsed.get(0).getObjectId());
        assertEquals(1759924158381L, parsed.get(0).getWallclockMillis());
        assertEquals(2002, parsed.get(1).getMediaPtsMillis());
        assertEquals(1, parsed.get(1).getGroupId());
    }

    @Test
    void testMediaTimelineJsonFormat() throws Exception {
        MsfMediaTimeline timeline = new MsfMediaTimeline();
        List<MsfMediaTimelineRecord> records = List.of(
            new MsfMediaTimelineRecord(0, 0, 0, 1759924158381L)
        );

        String json = timeline.toJson(records);
        // Verify JSON format matches MSF spec: [[pts, [groupId, objectId], wallclock]]
        assertTrue(json.contains("[0,[0,0],1759924158381]") || json.contains("[0, [0, 0], 1759924158381]"),
            "JSON should match MSF media timeline format");
    }

    @Test
    void testMediaTimelineGzipRoundTrip() throws Exception {
        MsfMediaTimeline timeline = new MsfMediaTimeline();
        List<MsfMediaTimelineRecord> records = List.of(
            new MsfMediaTimelineRecord(0, 0, 0, 1759924158381L),
            new MsfMediaTimelineRecord(2002, 1, 0, 1759924160383L)
        );

        byte[] gzip = timeline.toGzipJson(records);
        assertTrue(MsfMediaTimeline.isGzipCompressed(gzip));

        List<MsfMediaTimelineRecord> parsed = timeline.fromGzipJson(gzip);
        assertEquals(2, parsed.size());
        assertEquals(2002, parsed.get(1).getMediaPtsMillis());
    }

    @Test
    void testEventTimelineWallclockIndex() throws Exception {
        MsfEventTimeline timeline = new MsfEventTimeline();
        JsonObject data = new JsonObject();
        data.addProperty("status", "in_progress");
        data.addProperty("homeScore", 2);

        List<MsfEventTimelineEntry> entries = List.of(
            MsfEventTimelineEntry.withWallclock(1756885678361L, data)
        );

        String json = timeline.toJson(entries);
        assertTrue(json.contains("\"t\":1756885678361") || json.contains("\"t\": 1756885678361"));

        List<MsfEventTimelineEntry> parsed = timeline.fromJson(json);
        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0).isWallclockIndexed());
        assertEquals(1756885678361L, parsed.get(0).getWallclockMillis());
        assertEquals("in_progress", parsed.get(0).getData().getAsJsonObject().get("status").getAsString());
    }

    @Test
    void testEventTimelineLocationIndex() throws Exception {
        MsfEventTimeline timeline = new MsfEventTimeline();
        JsonObject data = new JsonObject();
        data.addProperty("lat", 47.1812);
        data.addProperty("lon", 8.4592);

        List<MsfEventTimelineEntry> entries = List.of(
            MsfEventTimelineEntry.withLocation(0, 0, data),
            MsfEventTimelineEntry.withLocation(1, 0, data)
        );

        String json = timeline.toJson(entries);
        assertTrue(json.contains("\"l\":[0,0]") || json.contains("\"l\": [0, 0]"));

        List<MsfEventTimelineEntry> parsed = timeline.fromJson(json);
        assertEquals(2, parsed.size());
        assertTrue(parsed.get(0).isLocationIndexed());
        assertEquals(0L, parsed.get(0).getGroupId());
        assertEquals(0L, parsed.get(0).getObjectId());
        assertTrue(parsed.get(1).isLocationIndexed());
        assertEquals(1L, parsed.get(1).getGroupId());
    }

    @Test
    void testEventTimelineMediaPtsIndex() throws Exception {
        MsfEventTimeline timeline = new MsfEventTimeline();
        JsonObject data = new JsonObject();
        data.addProperty("marker", "chapter_start");

        List<MsfEventTimelineEntry> entries = List.of(
            MsfEventTimelineEntry.withMediaPts(2002, data)
        );

        String json = timeline.toJson(entries);
        assertTrue(json.contains("\"m\":2002") || json.contains("\"m\": 2002"));

        List<MsfEventTimelineEntry> parsed = timeline.fromJson(json);
        assertEquals(1, parsed.size());
        assertTrue(parsed.get(0).isMediaPtsIndexed());
        assertEquals(2002L, parsed.get(0).getMediaPtsMillis());
    }

    @Test
    void testEventTimelineGzipRoundTrip() throws Exception {
        MsfEventTimeline timeline = new MsfEventTimeline();
        JsonObject data = new JsonObject();
        data.addProperty("event", "test");

        List<MsfEventTimelineEntry> entries = List.of(
            MsfEventTimelineEntry.withWallclock(1756885678361L, data)
        );

        byte[] gzip = timeline.toGzipJson(entries);
        assertTrue(MsfEventTimeline.isGzipCompressed(gzip));

        List<MsfEventTimelineEntry> parsed = timeline.fromGzipJson(gzip);
        assertEquals(1, parsed.size());
    }

    @Test
    void testEventTimelineMixedIndices() throws Exception {
        MsfEventTimeline timeline = new MsfEventTimeline();
        JsonObject data1 = new JsonObject();
        data1.addProperty("type", "wallclock");
        JsonObject data2 = new JsonObject();
        data2.addProperty("type", "location");
        JsonObject data3 = new JsonObject();
        data3.addProperty("type", "media");

        List<MsfEventTimelineEntry> entries = List.of(
            MsfEventTimelineEntry.withWallclock(1000L, data1),
            MsfEventTimelineEntry.withLocation(5, 10, data2),
            MsfEventTimelineEntry.withMediaPts(3000L, data3)
        );

        String json = timeline.toJson(entries);
        List<MsfEventTimelineEntry> parsed = timeline.fromJson(json);

        assertEquals(3, parsed.size());
        assertTrue(parsed.get(0).isWallclockIndexed());
        assertTrue(parsed.get(1).isLocationIndexed());
        assertTrue(parsed.get(2).isMediaPtsIndexed());
    }

    @Test
    void testGzipDetection() {
        // Valid GZIP magic bytes
        byte[] gzipData = new byte[]{(byte) 0x1F, (byte) 0x8B, 0x08, 0x00};
        assertTrue(MsfMediaTimeline.isGzipCompressed(gzipData));
        assertTrue(MsfEventTimeline.isGzipCompressed(gzipData));

        // Not GZIP
        byte[] plainData = new byte[]{0x5B, 0x5D}; // "[]"
        assertFalse(MsfMediaTimeline.isGzipCompressed(plainData));
        assertFalse(MsfEventTimeline.isGzipCompressed(plainData));

        // Edge cases
        assertFalse(MsfMediaTimeline.isGzipCompressed(null));
        assertFalse(MsfMediaTimeline.isGzipCompressed(new byte[0]));
        assertFalse(MsfMediaTimeline.isGzipCompressed(new byte[1]));
    }
}
