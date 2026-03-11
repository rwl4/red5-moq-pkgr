package org.red5.io.moq.catalog;

import java.util.List;

/**
 * Represents a track in the MoQ Catalog.
 *
 * A track object describes a single media track with its properties,
 * selection parameters, and relationships to other tracks.
 */
public class CatalogTrack {

    /** Track namespace (optional, inherits from catalog if not specified) */
    private String namespace;

    /** Track name (required) */
    private String name;

    /** Packaging type ("cmaf" or "loc") */
    private String packaging;

    /** Forwarding preference ("datagram", "track", "group", "object") */
    private String forwardingPreference;

    /** Human-readable label */
    private String label;

    /** Render group ID - tracks with same ID should be rendered together */
    private Integer renderGroup;

    /** Alternate group ID - tracks with same ID are alternates of each other */
    private Integer altGroup;

    /** Base64-encoded initialization data */
    private String initData;

    /** Track name of another track holding initialization data */
    private String initTrack;

    /** Selection parameters for track selection */
    private SelectionParameters selectionParams;

    /** Array of track names this track depends on */
    private List<String> depends;

    /** Temporal layer ID (for SVC) */
    private Integer temporalId;

    /** Spatial layer ID (for SVC) */
    private Integer spatialId;

    public CatalogTrack() {
    }

    public CatalogTrack(String name, String packaging) {
        this.name = name;
        this.packaging = packaging;
    }

    /**
     * Validates the track according to spec requirements.
     *
     * @throws IllegalStateException if track is invalid
     */
    public void validate() {
        if (name == null || name.isEmpty()) {
            throw new IllegalStateException("Track name is required");
        }

        if (packaging == null || packaging.isEmpty()) {
            throw new IllegalStateException("Track packaging is required");
        }

        if (!"cmaf".equals(packaging) && !"loc".equals(packaging)) {
            throw new IllegalStateException("Invalid packaging type: " + packaging +
                ". Must be 'cmaf' or 'loc'");
        }
    }

    // Getters and setters

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public String getForwardingPreference() {
        return forwardingPreference;
    }

    public void setForwardingPreference(String forwardingPreference) {
        this.forwardingPreference = forwardingPreference;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getRenderGroup() {
        return renderGroup;
    }

    public void setRenderGroup(Integer renderGroup) {
        this.renderGroup = renderGroup;
    }

    public Integer getAltGroup() {
        return altGroup;
    }

    public void setAltGroup(Integer altGroup) {
        this.altGroup = altGroup;
    }

    public String getInitData() {
        return initData;
    }

    public void setInitData(String initData) {
        this.initData = initData;
    }

    public String getInitTrack() {
        return initTrack;
    }

    public void setInitTrack(String initTrack) {
        this.initTrack = initTrack;
    }

    public SelectionParameters getSelectionParams() {
        return selectionParams;
    }

    public void setSelectionParams(SelectionParameters selectionParams) {
        this.selectionParams = selectionParams;
    }

    public List<String> getDepends() {
        return depends;
    }

    public void setDepends(List<String> depends) {
        this.depends = depends;
    }

    public Integer getTemporalId() {
        return temporalId;
    }

    public void setTemporalId(Integer temporalId) {
        this.temporalId = temporalId;
    }

    public Integer getSpatialId() {
        return spatialId;
    }

    public void setSpatialId(Integer spatialId) {
        this.spatialId = spatialId;
    }

    @Override
    public String toString() {
        return "CatalogTrack{" +
                "namespace='" + namespace + '\'' +
                ", name='" + name + '\'' +
                ", packaging='" + packaging + '\'' +
                ", label='" + label + '\'' +
                ", renderGroup=" + renderGroup +
                ", altGroup=" + altGroup +
                '}';
    }
}
