package org.red5.io.moq.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonSyntaxException;

/**
 * Unit tests for CatalogSerializer - IETF MoQ catalog JSON serialization.
 */
class CatalogSerializerTest {

    private CatalogSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new CatalogSerializer();
    }

    @Test
    void testSerialize_SimpleCatalog() {
        Catalog catalog = CatalogSerializer.createSimpleCatalog(1, "0.2", "live/stream1");

        String json = serializer.serialize(catalog);

        assertNotNull(json);
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"streamingFormat\":1"));
        assertTrue(json.contains("\"streamingFormatVersion\":\"0.2\""));
        assertTrue(json.contains("\"namespace\":\"live/stream1\""));
    }

    @Test
    void testSerialize_ExampleCatalog() {
        Catalog catalog = CatalogSerializer.createExampleCatalog("live/broadcast");

        String json = serializer.serialize(catalog);

        assertNotNull(json);
        assertTrue(json.contains("\"version\":1"));
        assertTrue(json.contains("\"tracks\""));
        assertTrue(json.contains("\"video\""));
        assertTrue(json.contains("\"audio\""));
        assertTrue(json.contains("av01.0.08M.10.0.110.09"));
        assertTrue(json.contains("opus"));
    }

    @Test
    void testSerialize_NullCatalog() {
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(null));
    }

    @Test
    void testSerializePretty_ProducesReadableOutput() {
        Catalog catalog = CatalogSerializer.createExampleCatalog("test");

        String json = serializer.serializePretty(catalog);

        assertNotNull(json);
        assertTrue(json.contains("\n"), "Pretty print should contain newlines");
        assertTrue(json.contains("  "), "Pretty print should contain indentation");
    }

    @Test
    void testSerializeToBytes_UTF8() {
        Catalog catalog = CatalogSerializer.createSimpleCatalog(1, "0.2", "test");

        byte[] bytes = serializer.serializeToBytes(catalog);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(json.contains("\"version\":1"));
    }

    @Test
    void testDeserialize_ValidJson() {
        String json = """
            {
                "version": 1,
                "streamingFormat": 1,
                "streamingFormatVersion": "0.2",
                "commonTrackFields": {
                    "namespace": "live/test",
                    "packaging": "loc"
                },
                "tracks": [
                    {
                        "name": "video",
                        "packaging": "loc"
                    }
                ]
            }
            """;

        Catalog catalog = serializer.deserialize(json);

        assertNotNull(catalog);
        assertEquals(1, catalog.getVersion());
        assertEquals(1, catalog.getStreamingFormat());
        assertEquals("0.2", catalog.getStreamingFormatVersion());
        assertNotNull(catalog.getCommonTrackFields());
        assertEquals("live/test", catalog.getCommonTrackFields().getNamespace());
        assertEquals(1, catalog.getTracks().size());
        assertEquals("video", catalog.getTracks().get(0).getName());
    }

    @Test
    void testDeserialize_NullJson() {
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize((String) null));
    }

    @Test
    void testDeserialize_EmptyJson() {
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(""));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize("   "));
    }

    @Test
    void testDeserialize_InvalidJson() {
        assertThrows(JsonSyntaxException.class, () -> serializer.deserialize("{invalid json}"));
    }

    @Test
    void testDeserialize_Bytes() {
        String json = "{\"version\":1,\"streamingFormat\":1,\"streamingFormatVersion\":\"0.2\",\"tracks\":[{\"name\":\"video\",\"packaging\":\"loc\"}]}";
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Catalog catalog = serializer.deserialize(bytes);

        assertNotNull(catalog);
        assertEquals(1, catalog.getVersion());
    }

    @Test
    void testDeserialize_NullBytes() {
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize((byte[]) null));
    }

    @Test
    void testDeserialize_EmptyBytes() {
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(new byte[0]));
    }

    @Test
    void testRoundTrip_PreservesData() {
        Catalog original = CatalogSerializer.createExampleCatalog("roundtrip/test");

        String json = serializer.serialize(original);
        Catalog deserialized = serializer.deserialize(json);

        assertEquals(original.getVersion(), deserialized.getVersion());
        assertEquals(original.getStreamingFormat(), deserialized.getStreamingFormat());
        assertEquals(original.getStreamingFormatVersion(), deserialized.getStreamingFormatVersion());
        assertEquals(original.getTracks().size(), deserialized.getTracks().size());
    }

    @Test
    void testCreateSimpleCatalog() {
        Catalog catalog = CatalogSerializer.createSimpleCatalog(2, "1.0", "myns");

        assertEquals(1, catalog.getVersion());
        assertEquals(2, catalog.getStreamingFormat());
        assertEquals("1.0", catalog.getStreamingFormatVersion());
        assertEquals("myns", catalog.getCommonTrackFields().getNamespace());
    }

    @Test
    void testCreateExampleCatalog_HasVideoAndAudio() {
        Catalog catalog = CatalogSerializer.createExampleCatalog("example");

        assertEquals(2, catalog.getTracks().size());

        CatalogTrack videoTrack = catalog.getTracks().stream()
            .filter(t -> "video".equals(t.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(videoTrack);
        assertNotNull(videoTrack.getSelectionParams());
        assertEquals(1920, videoTrack.getSelectionParams().getWidth());
        assertEquals(1080, videoTrack.getSelectionParams().getHeight());

        CatalogTrack audioTrack = catalog.getTracks().stream()
            .filter(t -> "audio".equals(t.getName()))
            .findFirst()
            .orElse(null);
        assertNotNull(audioTrack);
        assertNotNull(audioTrack.getSelectionParams());
        assertEquals("opus", audioTrack.getSelectionParams().getCodec());
    }
}
