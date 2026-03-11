package org.red5.io.moq.catalog;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.msf.catalog.MsfCatalog;
import org.red5.io.moq.msf.catalog.MsfTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MsfCatalogAdapterTest {

    @Test
    void testToCatalogMapsSharedSubset() {
        MsfCatalog msfCatalog = MsfCatalog.builder()
            .generatedAt(1234L)
            .addTrack(MsfTrack.video("video")
                .namespace("live/test")
                .live()
                .role("video"))
            .buildWithoutValidation();

        Catalog catalog = MsfCatalogAdapter.toCatalog(msfCatalog);

        assertEquals(MsfCatalogAdapter.STREAMING_FORMAT_MSF, catalog.getStreamingFormat());
        assertEquals(1234L, catalog.getGeneratedAt());
        assertEquals(1, catalog.getTracks().size());
        assertEquals("video", catalog.getTracks().get(0).getName());
        assertEquals("video", catalog.getTracks().get(0).getRole());
        assertTrue(catalog.getTracks().get(0).getIsLive());
    }

    @Test
    void testToMsfCatalogMapsSharedFields() {
        Catalog catalog = new Catalog(MsfCatalogAdapter.STREAMING_FORMAT_MSF, MsfCatalogAdapter.STREAMING_FORMAT_VERSION_MSF);
        catalog.setVersion(1);
        catalog.setGeneratedAt(1234L);

        CommonTrackFields common = new CommonTrackFields();
        common.setNamespace("live/test");
        common.setPackaging("loc");
        catalog.setCommonTrackFields(common);

        CatalogTrack video = new CatalogTrack("video", null);
        SelectionParameters params = new SelectionParameters();
        params.setCodec("av01");
        video.setSelectionParams(params);
        video.setIsLive(true);
        video.setRole("video");
        catalog.setTracks(List.of(video));

        MsfCatalog msfCatalog = MsfCatalogAdapter.toMsfCatalog(catalog);

        assertEquals(1234L, msfCatalog.getGeneratedAt());
        assertEquals(1, msfCatalog.getTracks().size());
        assertEquals("live/test", msfCatalog.getTracks().get(0).getNamespace());
        assertEquals("av01", msfCatalog.getTracks().get(0).getCodec());
        assertTrue(msfCatalog.getTracks().get(0).getIsLive());
        assertEquals("video", msfCatalog.getTracks().get(0).getRole());
    }
}
