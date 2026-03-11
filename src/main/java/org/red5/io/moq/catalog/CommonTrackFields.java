package org.red5.io.moq.catalog;

/**
 * Common Track Fields object holding fields inherited by all tracks.
 *
 * Fields defined at the Track object level always supercede any value inherited
 * from the Common Track Fields object.
 */
public class CommonTrackFields {

    /** Track namespace */
    private String namespace;

    /** Packaging type ("cmaf" or "loc") */
    private String packaging;

    /** Render group ID */
    private Integer renderGroup;

    /** Selection parameters inherited by all tracks */
    private SelectionParameters selectionParams;

    public CommonTrackFields() {
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public Integer getRenderGroup() {
        return renderGroup;
    }

    public void setRenderGroup(Integer renderGroup) {
        this.renderGroup = renderGroup;
    }

    public SelectionParameters getSelectionParams() {
        return selectionParams;
    }

    public void setSelectionParams(SelectionParameters selectionParams) {
        this.selectionParams = selectionParams;
    }

    @Override
    public String toString() {
        return "CommonTrackFields{" +
                "namespace='" + namespace + '\'' +
                ", packaging='" + packaging + '\'' +
                ", renderGroup=" + renderGroup +
                '}';
    }
}
