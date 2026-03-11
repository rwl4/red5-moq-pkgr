package org.red5.io.moq.catalog;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.warp.catalog.WarpCatalog;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WarpCatalogAdapterTest {

    @Test
    void testToCatalogMapsSharedFields() {
        WarpTrack video = new WarpTrack();
        video.setNamespace("live/test");
        video.setName("video");
        video.setPackaging("loc");
        video.setRenderGroup(1);
        video.setIsLive(true);
        video.setRole("video");
        video.setCodec("av01");
        video.setWidth(1920);
        video.setHeight(1080);
        video.setFramerate(30);
        video.setTimescale(90000);
        video.setBitrate(1_500_000);

        WarpTrack audio = new WarpTrack();
        audio.setNamespace("live/test");
        audio.setName("audio");
        audio.setPackaging("loc");
        audio.setRenderGroup(1);
        audio.setIsLive(true);
        audio.setRole("audio");
        audio.setCodec("opus");
        audio.setSamplerate(48000);
        audio.setChannelConfig("2");
        audio.setBitrate(32000);

        WarpCatalog warpCatalog = new WarpCatalog();
        warpCatalog.setVersion(1);
        warpCatalog.setTracks(List.of(video, audio));

        Catalog catalog = WarpCatalogAdapter.toCatalog(warpCatalog);

        assertEquals(WarpCatalogAdapter.STREAMING_FORMAT_WARP, catalog.getStreamingFormat());
        assertEquals("live/test", catalog.getCommonTrackFields().getNamespace());
        assertEquals("loc", catalog.getCommonTrackFields().getPackaging());
        assertEquals(1, catalog.getCommonTrackFields().getRenderGroup());
        assertEquals(2, catalog.getTracks().size());
        assertNull(catalog.getTracks().get(0).getNamespace());
        assertEquals("av01", catalog.getTracks().get(0).getSelectionParams().getCodec());
        assertTrue(catalog.getTracks().get(0).getIsLive());
        assertEquals("video", catalog.getTracks().get(0).getRole());
        assertEquals(90000, catalog.getTracks().get(0).getSelectionParams().getTimescale());
    }

    @Test
    void testToWarpCatalogMapsSharedFields() {
        Catalog catalog = new Catalog(WarpCatalogAdapter.STREAMING_FORMAT_WARP, WarpCatalogAdapter.STREAMING_FORMAT_VERSION_WARP);
        catalog.setVersion(1);

        CommonTrackFields common = new CommonTrackFields();
        common.setNamespace("live/test");
        common.setPackaging("loc");
        common.setRenderGroup(1);
        catalog.setCommonTrackFields(common);

        CatalogTrack video = new CatalogTrack("video", null);
        SelectionParameters selectionParameters = new SelectionParameters();
        selectionParameters.setCodec("av01");
        selectionParameters.setWidth(1920);
        selectionParameters.setHeight(1080);
        selectionParameters.setFramerate(30.0);
        selectionParameters.setTimescale(90000);
        video.setSelectionParams(selectionParameters);
        video.setIsLive(true);
        video.setRole("video");
        catalog.setTracks(List.of(video));

        WarpCatalog warpCatalog = WarpCatalogAdapter.toWarpCatalog(catalog);

        assertEquals(1, warpCatalog.getVersion());
        assertEquals(1, warpCatalog.getTracks().size());
        assertEquals("live/test", warpCatalog.getTracks().get(0).getNamespace());
        assertEquals("loc", warpCatalog.getTracks().get(0).getPackaging());
        assertEquals("av01", warpCatalog.getTracks().get(0).getCodec());
        assertEquals(30, warpCatalog.getTracks().get(0).getFramerate());
        assertEquals(90000, warpCatalog.getTracks().get(0).getTimescale());
        assertTrue(warpCatalog.getTracks().get(0).getIsLive());
        assertEquals("video", warpCatalog.getTracks().get(0).getRole());
    }

    @Test
    void testToWarpCatalogRejectsNestedCatalogs() {
        Catalog root = new Catalog(WarpCatalogAdapter.STREAMING_FORMAT_WARP, WarpCatalogAdapter.STREAMING_FORMAT_VERSION_WARP);
        Catalog child = new Catalog(WarpCatalogAdapter.STREAMING_FORMAT_WARP, WarpCatalogAdapter.STREAMING_FORMAT_VERSION_WARP);
        child.setTracks(List.of(new CatalogTrack("video", "loc")));
        root.setCatalogs(List.of(child));

        assertThrows(IllegalArgumentException.class, () -> WarpCatalogAdapter.toWarpCatalog(root));
    }
}
