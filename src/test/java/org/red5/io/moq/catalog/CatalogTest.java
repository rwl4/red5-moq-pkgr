package org.red5.io.moq.catalog;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Catalog - IETF MoQ catalog data model.
 */
class CatalogTest {

    @Test
    void testDefaultConstructor() {
        Catalog catalog = new Catalog();

        assertEquals(1, catalog.getVersion());
        assertEquals(0, catalog.getStreamingFormat());
        assertNull(catalog.getStreamingFormatVersion());
        assertNull(catalog.getTracks());
        assertNull(catalog.getCatalogs());
    }

    @Test
    void testParameterizedConstructor() {
        Catalog catalog = new Catalog(1, "0.2");

        assertEquals(1, catalog.getVersion());
        assertEquals(1, catalog.getStreamingFormat());
        assertEquals("0.2", catalog.getStreamingFormatVersion());
    }

    @Test
    void testSettersAndGetters() {
        Catalog catalog = new Catalog();

        catalog.setVersion(1);
        catalog.setStreamingFormat(2);
        catalog.setStreamingFormatVersion("1.0");
        catalog.setSupportsDeltaUpdates(true);

        assertEquals(1, catalog.getVersion());
        assertEquals(2, catalog.getStreamingFormat());
        assertEquals("1.0", catalog.getStreamingFormatVersion());
        assertTrue(catalog.getSupportsDeltaUpdates());
    }

    @Test
    void testCommonTrackFields() {
        Catalog catalog = new Catalog(1, "0.2");

        CommonTrackFields common = new CommonTrackFields();
        common.setNamespace("live/stream");
        common.setPackaging("loc");
        common.setRenderGroup(1);
        catalog.setCommonTrackFields(common);

        assertNotNull(catalog.getCommonTrackFields());
        assertEquals("live/stream", catalog.getCommonTrackFields().getNamespace());
        assertEquals("loc", catalog.getCommonTrackFields().getPackaging());
        assertEquals(1, catalog.getCommonTrackFields().getRenderGroup());
    }

    @Test
    void testTracks() {
        Catalog catalog = new Catalog(1, "0.2");

        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        tracks.add(new CatalogTrack("audio", "loc"));
        catalog.setTracks(tracks);

        assertEquals(2, catalog.getTracks().size());
        assertEquals("video", catalog.getTracks().get(0).getName());
        assertEquals("audio", catalog.getTracks().get(1).getName());
    }

    @Test
    void testNestedCatalogs() {
        Catalog parent = new Catalog(1, "0.2");

        Catalog child = new Catalog(1, "0.2");
        List<CatalogTrack> childTracks = new ArrayList<>();
        childTracks.add(new CatalogTrack("video", "loc"));
        child.setTracks(childTracks);

        List<Catalog> catalogs = new ArrayList<>();
        catalogs.add(child);
        parent.setCatalogs(catalogs);

        assertEquals(1, parent.getCatalogs().size());
    }

    @Test
    void testValidate_ValidCatalog() {
        Catalog catalog = new Catalog(1, "0.2");
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        assertDoesNotThrow(catalog::validate);
    }

    @Test
    void testValidate_InvalidVersion() {
        Catalog catalog = new Catalog(1, "0.2");
        catalog.setVersion(2);
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("Unsupported catalog version"));
    }

    @Test
    void testValidate_MissingStreamingFormat() {
        Catalog catalog = new Catalog();
        catalog.setStreamingFormat(0);
        catalog.setStreamingFormatVersion("0.2");
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("streamingFormat is required"));
    }

    @Test
    void testValidate_MissingStreamingFormatVersion() {
        Catalog catalog = new Catalog();
        catalog.setStreamingFormat(1);
        catalog.setStreamingFormatVersion(null);
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("streamingFormatVersion is required"));
    }

    @Test
    void testValidate_EmptyStreamingFormatVersion() {
        Catalog catalog = new Catalog();
        catalog.setStreamingFormat(1);
        catalog.setStreamingFormatVersion("");
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("streamingFormatVersion is required"));
    }

    @Test
    void testValidate_NoTracksOrCatalogs() {
        Catalog catalog = new Catalog(1, "0.2");

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("must have either 'tracks' or 'catalogs'"));
    }

    @Test
    void testValidate_BothTracksAndCatalogs() {
        Catalog catalog = new Catalog(1, "0.2");

        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        Catalog child = new Catalog(1, "0.2");
        List<CatalogTrack> childTracks = new ArrayList<>();
        childTracks.add(new CatalogTrack("audio", "loc"));
        child.setTracks(childTracks);

        List<Catalog> catalogs = new ArrayList<>();
        catalogs.add(child);
        catalog.setCatalogs(catalogs);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("cannot have both 'tracks' and 'catalogs'"));
    }

    @Test
    void testValidate_DuplicateTrackName() {
        Catalog catalog = new Catalog(1, "0.2");

        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        tracks.add(new CatalogTrack("video", "loc")); // duplicate
        catalog.setTracks(tracks);

        IllegalStateException ex = assertThrows(IllegalStateException.class, catalog::validate);
        assertTrue(ex.getMessage().contains("Duplicate track name"));
    }

    @Test
    void testValidate_WithNestedCatalogs() {
        Catalog parent = new Catalog(1, "0.2");

        Catalog child = new Catalog(1, "0.2");
        List<CatalogTrack> childTracks = new ArrayList<>();
        childTracks.add(new CatalogTrack("video", "loc"));
        child.setTracks(childTracks);

        List<Catalog> catalogs = new ArrayList<>();
        catalogs.add(child);
        parent.setCatalogs(catalogs);

        assertDoesNotThrow(parent::validate);
    }

    @Test
    void testToString() {
        Catalog catalog = new Catalog(1, "0.2");
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        String str = catalog.toString();

        assertTrue(str.contains("version=1"));
        assertTrue(str.contains("streamingFormat=1"));
        assertTrue(str.contains("streamingFormatVersion='0.2'"));
        assertTrue(str.contains("tracksCount=1"));
    }

    @Test
    void testCatalogNameAndNamespace() {
        Catalog catalog = new Catalog(1, "0.2");
        catalog.setName("catalog-for-stream1");
        catalog.setNamespace("live/stream1");

        assertEquals("catalog-for-stream1", catalog.getName());
        assertEquals("live/stream1", catalog.getNamespace());
    }

    @Test
    void testCatalogOfCatalogs_ValidStructure() {
        Catalog root = new Catalog(1, "0.2");

        Catalog child1 = new Catalog(1, "0.2");
        child1.setName("catalog");
        child1.setNamespace("live/stream1");
        List<CatalogTrack> child1Tracks = new ArrayList<>();
        child1Tracks.add(new CatalogTrack("video", "loc"));
        child1.setTracks(child1Tracks);

        Catalog child2 = new Catalog(1, "0.2");
        child2.setName("catalog");
        child2.setNamespace("live/stream2");
        List<CatalogTrack> child2Tracks = new ArrayList<>();
        child2Tracks.add(new CatalogTrack("audio", "loc"));
        child2.setTracks(child2Tracks);

        List<Catalog> catalogs = new ArrayList<>();
        catalogs.add(child1);
        catalogs.add(child2);
        root.setCatalogs(catalogs);

        assertDoesNotThrow(root::validate);
        assertEquals(2, root.getCatalogs().size());
        assertEquals("catalog", root.getCatalogs().get(0).getName());
        assertEquals("live/stream1", root.getCatalogs().get(0).getNamespace());
    }

    @Test
    void testToString_IncludesNameAndNamespace() {
        Catalog catalog = new Catalog(1, "0.2");
        catalog.setName("my-catalog");
        catalog.setNamespace("live/test");
        List<CatalogTrack> tracks = new ArrayList<>();
        tracks.add(new CatalogTrack("video", "loc"));
        catalog.setTracks(tracks);

        String str = catalog.toString();
        assertTrue(str.contains("name='my-catalog'"));
        assertTrue(str.contains("namespace='live/test'"));
    }
}
