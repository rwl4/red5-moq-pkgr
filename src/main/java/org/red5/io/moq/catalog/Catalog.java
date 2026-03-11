package org.red5.io.moq.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents a MoQ Catalog as defined in draft-ietf-moq-catalogformat.
 *
 * A Catalog is a MOQT Object that provides information about tracks from a given publisher.
 * The catalog is a JSON document describing tracks or other catalogs.
 */
public class Catalog {

    /** Catalog version (this implementation supports version 1) */
    private int version = 1;

    /** Streaming format type (e.g., 1 for LOC, 2 for CMAF, 10 for MOQMI) */
    private int streamingFormat;

    /** Streaming format version string */
    private String streamingFormatVersion;

    /** Track name for this catalog (used in catalog-of-catalogs entries) */
    private String name;

    /** Track namespace for this catalog (used in catalog-of-catalogs entries) */
    private String namespace;

    /** Whether publisher may issue incremental (delta) updates */
    private Boolean supportsDeltaUpdates;

    /** Common fields inherited by all tracks */
    private CommonTrackFields commonTrackFields;

    /** Array of track objects (mutually exclusive with catalogs) */
    private List<CatalogTrack> tracks;

    /** Array of catalog objects (mutually exclusive with tracks) */
    private List<Catalog> catalogs;

    public Catalog() {
    }

    public Catalog(int streamingFormat, String streamingFormatVersion) {
        this.streamingFormat = streamingFormat;
        this.streamingFormatVersion = streamingFormatVersion;
    }

    // Getters and setters

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public int getStreamingFormat() {
        return streamingFormat;
    }

    public void setStreamingFormat(int streamingFormat) {
        this.streamingFormat = streamingFormat;
    }

    public String getStreamingFormatVersion() {
        return streamingFormatVersion;
    }

    public void setStreamingFormatVersion(String streamingFormatVersion) {
        this.streamingFormatVersion = streamingFormatVersion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Boolean getSupportsDeltaUpdates() {
        return supportsDeltaUpdates;
    }

    public void setSupportsDeltaUpdates(Boolean supportsDeltaUpdates) {
        this.supportsDeltaUpdates = supportsDeltaUpdates;
    }

    public CommonTrackFields getCommonTrackFields() {
        return commonTrackFields;
    }

    public void setCommonTrackFields(CommonTrackFields commonTrackFields) {
        this.commonTrackFields = commonTrackFields;
    }

    public List<CatalogTrack> getTracks() {
        return tracks;
    }

    public void setTracks(List<CatalogTrack> tracks) {
        this.tracks = tracks;
    }

    public List<Catalog> getCatalogs() {
        return catalogs;
    }

    public void setCatalogs(List<Catalog> catalogs) {
        this.catalogs = catalogs;
    }

    /**
     * Validates the catalog structure according to spec requirements.
     *
     * @throws IllegalStateException if catalog is invalid
     */
    public void validate() {
        if (version != 1) {
            throw new IllegalStateException("Unsupported catalog version: " + version);
        }

        if (streamingFormat == 0) {
            throw new IllegalStateException("streamingFormat is required");
        }

        if (streamingFormatVersion == null || streamingFormatVersion.isEmpty()) {
            throw new IllegalStateException("streamingFormatVersion is required");
        }

        // Must have either tracks or catalogs, but not both
        if ((tracks == null || tracks.isEmpty()) && (catalogs == null || catalogs.isEmpty())) {
            throw new IllegalStateException("Catalog must have either 'tracks' or 'catalogs' field");
        }

        if (tracks != null && !tracks.isEmpty() && catalogs != null && !catalogs.isEmpty()) {
            throw new IllegalStateException("Catalog cannot have both 'tracks' and 'catalogs' fields");
        }

        // Validate tracks
        if (tracks != null) {
            Set<String> trackNames = new HashSet<>();
            for (CatalogTrack track : tracks) {
                track.validate();
                // Check track name uniqueness per namespace
                String key = (track.getNamespace() != null ? track.getNamespace() : "") + "/" + track.getName();
                if (trackNames.contains(key)) {
                    throw new IllegalStateException("Duplicate track name in namespace: " + key);
                }
                trackNames.add(key);
            }
        }

        // Validate nested catalogs
        if (catalogs != null) {
            for (Catalog catalog : catalogs) {
                catalog.validate();
            }
        }
    }

    @Override
    public String toString() {
        return "Catalog{" +
                "version=" + version +
                ", name='" + name + '\'' +
                ", namespace='" + namespace + '\'' +
                ", streamingFormat=" + streamingFormat +
                ", streamingFormatVersion='" + streamingFormatVersion + '\'' +
                ", supportsDeltaUpdates=" + supportsDeltaUpdates +
                ", tracksCount=" + (tracks != null ? tracks.size() : 0) +
                ", catalogsCount=" + (catalogs != null ? catalogs.size() : 0) +
                '}';
    }
}
