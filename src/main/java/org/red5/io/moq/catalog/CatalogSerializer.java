package org.red5.io.moq.catalog;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * Serializes and deserializes MoQ Catalogs to/from JSON.
 *
 * Uses Gson for JSON processing with custom configuration to handle:
 * - Null value exclusion (only serialize non-null fields)
 * - Pretty printing for human-readable output
 */
public class CatalogSerializer {

    private final Gson gson, prettyGson;

    public CatalogSerializer() {
        // Create Gson instance that excludes null values
        this.gson = new GsonBuilder()
            .create();

        // Create pretty-printing Gson for debugging
        this.prettyGson = new GsonBuilder()
            .setPrettyPrinting()
            .create();
    }

    protected String serializeObject(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        return gson.toJson(value);
    }

    protected String serializePrettyObject(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        return prettyGson.toJson(value);
    }

    protected <T> T deserializeObject(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        try {
            T value = gson.fromJson(json, type);
            if (value == null) {
                throw new JsonSyntaxException("Failed to deserialize object");
            }
            return value;
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes a catalog to JSON string.
     *
     * @param catalog the catalog to serialize
     * @return JSON string representation
     */
    public String serialize(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }
        return serializeObject(catalog);
    }

    /**
     * Serializes a catalog to pretty-printed JSON string.
     *
     * @param catalog the catalog to serialize
     * @return pretty-printed JSON string
     */
    public String serializePretty(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }
        return serializePrettyObject(catalog);
    }

    /**
     * Serializes a catalog to JSON bytes (UTF-8).
     *
     * @param catalog the catalog to serialize
     * @return JSON bytes
     */
    public byte[] serializeToBytes(Catalog catalog) {
        return serialize(catalog).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Deserializes a catalog from JSON string.
     *
     * @param json JSON string
     * @return deserialized catalog
     * @throws JsonSyntaxException if JSON is malformed
     * @throws IllegalStateException if catalog validation fails
     */
    public Catalog deserialize(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        try {
            Catalog catalog = deserializeObject(json, Catalog.class);
            // Validate the catalog structure
            catalog.validate();
            return catalog;
        } catch (JsonSyntaxException e) {
            throw new JsonSyntaxException("Invalid catalog JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Deserializes a catalog from JSON bytes (UTF-8).
     *
     * @param bytes JSON bytes
     * @return deserialized catalog
     * @throws JsonSyntaxException if JSON is malformed
     * @throws IllegalStateException if catalog validation fails
     */
    public Catalog deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("JSON bytes cannot be null or empty");
        }
        String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return deserialize(json);
    }

    /**
     * Creates a simple catalog for testing or basic use cases.
     *
     * @param streamingFormat streaming format type
     * @param streamingFormatVersion streaming format version
     * @param namespace common namespace for tracks
     * @return new catalog instance
     */
    public static Catalog createSimpleCatalog(int streamingFormat, String streamingFormatVersion,
                                              String namespace) {
        Catalog catalog = new Catalog(streamingFormat, streamingFormatVersion);
        CommonTrackFields common = new CommonTrackFields();
        common.setNamespace(namespace);
        catalog.setCommonTrackFields(common);
        return catalog;
    }

    /**
     * Creates an example video + audio catalog.
     *
     * @param namespace track namespace
     * @return example catalog
     */
    public static Catalog createExampleCatalog(String namespace) {
        Catalog catalog = createSimpleCatalog(1, "0.2", namespace);
        CommonTrackFields common = catalog.getCommonTrackFields();
        common.setPackaging("loc");
        common.setRenderGroup(1);
        List<CatalogTrack> tracks = new ArrayList<>();
        // Video track
        CatalogTrack video = new CatalogTrack("video", "loc");
        SelectionParameters videoParams = new SelectionParameters();
        videoParams.setCodec("av01.0.08M.10.0.110.09");
        videoParams.setWidth(1920);
        videoParams.setHeight(1080);
        videoParams.setFramerate(30.0);
        videoParams.setBitrate(1500000L);
        video.setSelectionParams(videoParams);
        tracks.add(video);
        // Audio track
        CatalogTrack audio = new CatalogTrack("audio", "loc");
        SelectionParameters audioParams = new SelectionParameters();
        audioParams.setCodec("opus");
        audioParams.setSamplerate(48000);
        audioParams.setChannelConfig("2");
        audioParams.setBitrate(32000L);
        audio.setSelectionParams(audioParams);
        tracks.add(audio);
        catalog.setTracks(tracks);
        return catalog;
    }
}
