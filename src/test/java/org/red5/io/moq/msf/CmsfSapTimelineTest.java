package org.red5.io.moq.msf;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.timeline.CmsfSapTimeline;
import org.red5.io.moq.msf.timeline.CmsfSapTimelineEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CmsfSapTimelineTest {

    private final CmsfSapTimeline timeline = new CmsfSapTimeline();

    @Test
    void testRoundTripJson() throws Exception {
        List<CmsfSapTimelineEntry> entries = List.of(
            new CmsfSapTimelineEntry(0, 0, 2, 0),
            new CmsfSapTimelineEntry(0, 60, 3, 2100)
        );

        String json = timeline.toJson(entries);
        List<CmsfSapTimelineEntry> parsed = timeline.fromJson(json);

        assertEquals(2, parsed.size());
        assertEquals(0, parsed.get(0).getGroupId());
        assertEquals(0, parsed.get(0).getObjectId());
        assertEquals(2, parsed.get(0).getSapType());
        assertEquals(2100, parsed.get(1).getEarliestPresentationTimeMs());
    }

    @Test
    void testRoundTripGzipJson() throws Exception {
        List<CmsfSapTimelineEntry> entries = List.of(new CmsfSapTimelineEntry(1, 0, 1, 4000));

        byte[] gzip = timeline.toGzipJson(entries);
        List<CmsfSapTimelineEntry> parsed = timeline.fromGzipJson(gzip);

        assertEquals(1, parsed.size());
        assertEquals(1, parsed.get(0).getGroupId());
        assertEquals(1, parsed.get(0).getSapType());
    }

    @Test
    void testRejectsNonLocationIndex() {
        String json = "[{\"m\":1000,\"data\":[1,1000]}]";
        assertThrows(Exception.class, () -> timeline.fromJson(json));
    }

    @Test
    void testRejectsInvalidFirstObjectSapType() {
        List<CmsfSapTimelineEntry> entries = List.of(new CmsfSapTimelineEntry(0, 0, 3, 0));
        assertThrows(IllegalArgumentException.class, () -> timeline.toJson(entries));
    }

    @Test
    void testRejectsInvalidDataShape() {
        String json = "[{\"l\":[0,0],\"data\":[1]}]";
        assertThrows(Exception.class, () -> timeline.fromJson(json));
    }
}
