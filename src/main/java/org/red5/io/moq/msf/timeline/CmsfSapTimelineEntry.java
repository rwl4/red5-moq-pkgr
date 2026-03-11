package org.red5.io.moq.msf.timeline;

/**
 * Typed CMSF SAP timeline entry.
 *
 * CMSF requires location indexing with data encoded as:
 * { "l": [groupId, objectId], "data": [sapType, earliestPresentationTimeMs] }
 */
public class CmsfSapTimelineEntry {
    private final long groupId;
    private final long objectId;
    private final int sapType;
    private final long earliestPresentationTimeMs;

    public CmsfSapTimelineEntry(long groupId, long objectId, int sapType, long earliestPresentationTimeMs) {
        this.groupId = groupId;
        this.objectId = objectId;
        this.sapType = sapType;
        this.earliestPresentationTimeMs = earliestPresentationTimeMs;
    }

    public long getGroupId() {
        return groupId;
    }

    public long getObjectId() {
        return objectId;
    }

    public int getSapType() {
        return sapType;
    }

    public long getEarliestPresentationTimeMs() {
        return earliestPresentationTimeMs;
    }
}
