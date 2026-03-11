package org.red5.io.moq.msf.timeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Serializer/deserializer for the CMSF SAP timeline profile.
 */
public class CmsfSapTimeline {
    public static final String EVENT_TYPE = "org.ietf.moq.cmsf.sap";

    private final MsfEventTimeline delegate = new MsfEventTimeline();

    public String toJson(List<CmsfSapTimelineEntry> entries) {
        List<MsfEventTimelineEntry> genericEntries = new ArrayList<>(entries.size());
        for (CmsfSapTimelineEntry entry : entries) {
            validateEntry(entry);
            JsonArray data = new JsonArray();
            data.add(entry.getSapType());
            data.add(entry.getEarliestPresentationTimeMs());
            genericEntries.add(MsfEventTimelineEntry.withLocation(entry.getGroupId(), entry.getObjectId(), data));
        }
        return delegate.toJson(genericEntries);
    }

    public List<CmsfSapTimelineEntry> fromJson(String json) throws IOException {
        List<MsfEventTimelineEntry> genericEntries = delegate.fromJson(json);
        List<CmsfSapTimelineEntry> entries = new ArrayList<>(genericEntries.size());
        for (MsfEventTimelineEntry genericEntry : genericEntries) {
            if (!genericEntry.isLocationIndexed()) {
                throw new IOException("CMSF SAP timeline entries must use location indexing");
            }
            entries.add(fromGeneric(genericEntry));
        }
        return entries;
    }

    public byte[] toGzipJson(List<CmsfSapTimelineEntry> entries) throws IOException {
        return delegate.toGzipJson(toGeneric(entries));
    }

    public List<CmsfSapTimelineEntry> fromGzipJson(byte[] gzipData) throws IOException {
        List<MsfEventTimelineEntry> genericEntries = delegate.fromGzipJson(gzipData);
        List<CmsfSapTimelineEntry> entries = new ArrayList<>(genericEntries.size());
        for (MsfEventTimelineEntry genericEntry : genericEntries) {
            if (!genericEntry.isLocationIndexed()) {
                throw new IOException("CMSF SAP timeline entries must use location indexing");
            }
            entries.add(fromGeneric(genericEntry));
        }
        return entries;
    }

    private List<MsfEventTimelineEntry> toGeneric(List<CmsfSapTimelineEntry> entries) {
        List<MsfEventTimelineEntry> genericEntries = new ArrayList<>(entries.size());
        for (CmsfSapTimelineEntry entry : entries) {
            validateEntry(entry);
            JsonArray data = new JsonArray();
            data.add(entry.getSapType());
            data.add(entry.getEarliestPresentationTimeMs());
            genericEntries.add(MsfEventTimelineEntry.withLocation(entry.getGroupId(), entry.getObjectId(), data));
        }
        return genericEntries;
    }

    private CmsfSapTimelineEntry fromGeneric(MsfEventTimelineEntry genericEntry) throws IOException {
        JsonElement dataElement = genericEntry.getData();
        if (dataElement == null || !dataElement.isJsonArray()) {
            throw new IOException("CMSF SAP timeline data must be a JSON array");
        }
        JsonArray dataArray = dataElement.getAsJsonArray();
        if (dataArray.size() != 2) {
            throw new IOException("CMSF SAP timeline data must contain exactly two integers");
        }
        int sapType = dataArray.get(0).getAsInt();
        long earliestPresentationTimeMs = dataArray.get(1).getAsLong();
        CmsfSapTimelineEntry entry = new CmsfSapTimelineEntry(
            genericEntry.getGroupId(),
            genericEntry.getObjectId(),
            sapType,
            earliestPresentationTimeMs
        );
        validateEntry(entry);
        return entry;
    }

    private void validateEntry(CmsfSapTimelineEntry entry) {
        if (entry.getGroupId() < 0) {
            throw new IllegalArgumentException("groupId must be non-negative");
        }
        if (entry.getObjectId() < 0) {
            throw new IllegalArgumentException("objectId must be non-negative");
        }
        if (entry.getSapType() < 0 || entry.getSapType() > 3) {
            throw new IllegalArgumentException("sapType must be between 0 and 3");
        }
        if (entry.getObjectId() == 0 && entry.getSapType() != 1 && entry.getSapType() != 2) {
            throw new IllegalArgumentException("The first object in a group must use SAP type 1 or 2");
        }
        if (entry.getEarliestPresentationTimeMs() < 0) {
            throw new IllegalArgumentException("earliestPresentationTimeMs must be non-negative");
        }
    }
}
