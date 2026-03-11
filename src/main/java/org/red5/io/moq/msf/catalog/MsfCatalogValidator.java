package org.red5.io.moq.msf.catalog;

import org.red5.io.moq.warp.catalog.WarpCatalog;
import org.red5.io.moq.warp.catalog.WarpCatalogValidator;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MSF catalog validation rules per draft-ietf-moq-msf.
 * Extends WARP validation with MSF-specific requirements.
 */
public class MsfCatalogValidator {

    /**
     * Validate a catalog against MSF rules.
     */
    public static void validateCatalog(WarpCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }

        // MSF 5.3.9: isComplete=true with empty tracks is valid for broadcast termination
        boolean isTermination = Boolean.TRUE.equals(catalog.getIsComplete())
            && (catalog.getTracks() == null || catalog.getTracks().isEmpty())
            && !Boolean.TRUE.equals(catalog.getDeltaUpdate());

        if (isTermination) {
            // Validate termination catalog - only version and isComplete required
            if (catalog.getVersion() == null) {
                throw new IllegalArgumentException("Catalog version is required");
            }
            return;
        }

        // First apply base WARP validation with MSF packaging types
        WarpCatalogValidator.validateCatalog(catalog, PackagingType.allValues());

        // Then apply MSF-specific validation
        validateMsfRules(catalog);
    }

    private static void validateMsfRules(WarpCatalog catalog) {
        if (catalog == null) {
            return;
        }

        boolean isDelta = Boolean.TRUE.equals(catalog.getDeltaUpdate());

        // MSF 5.1.7: isComplete validation
        if (catalog.getIsComplete() != null) {
            if (Boolean.FALSE.equals(catalog.getIsComplete())) {
                throw new IllegalArgumentException("isComplete must not be included if false");
            }
            // If isComplete is true, tracks should be empty (per section 5.3.9)
            if (Boolean.TRUE.equals(catalog.getIsComplete()) && !isDelta) {
                if (catalog.getTracks() != null && !catalog.getTracks().isEmpty()) {
                    // Check all tracks - this is for termination scenario
                    // Actually, the spec says tracks array can be empty for termination
                }
            }
        }

        // Validate tracks
        if (!isDelta && catalog.getTracks() != null) {
            validateMsfTracks(catalog.getTracks());
            validateRenderGroupLatency(catalog.getTracks());
            validateAltGroupLatency(catalog.getTracks());
        }

        if (isDelta) {
            if (catalog.getAddTracks() != null) {
                validateMsfTracks(catalog.getAddTracks());
            }
            if (catalog.getCloneTracks() != null) {
                validateMsfTracks(catalog.getCloneTracks());
            }
        }
    }

    private static void validateMsfTracks(List<WarpTrack> tracks) {
        for (WarpTrack track : tracks) {
            validateMsfTrack(track);
        }
    }

    private static void validateMsfTrack(WarpTrack track) {
        String packaging = track.getPackaging();

        // MSF 5.1.16: targetLatency validation
        if (track.getTargetLatency() != null) {
            if (Boolean.FALSE.equals(track.getIsLive())) {
                throw new IllegalArgumentException("targetLatency must not be included if isLive is false");
            }
        }

        // MSF 5.1.37: trackDuration validation (inverted from WARP)
        if (track.getTrackDuration() != null) {
            if (Boolean.TRUE.equals(track.getIsLive())) {
                throw new IllegalArgumentException("trackDuration must not be included if isLive is true");
            }
        }

        // MSF Section 7.2: Media Timeline track requirements
        if (PackagingType.MEDIA_TIMELINE.getValue().equals(packaging)) {
            validateMediaTimelineTrack(track);
        }

        // MSF Section 8.2: Event Timeline track requirements
        if (PackagingType.EVENT_TIMELINE.getValue().equals(packaging)) {
            validateEventTimelineTrack(track);
        }

        if (PackagingType.CMAF.getValue().equals(packaging)) {
            validateCmsfTrack(track);
        }
    }

    /**
     * MSF Section 7.2: Media Timeline Catalog requirements
     */
    private static void validateMediaTimelineTrack(WarpTrack track) {
        if (isEmpty(track.getDepends())) {
            throw new IllegalArgumentException("Media timeline track must declare depends");
        }
        if (!"application/json".equals(track.getMimeType())) {
            throw new IllegalArgumentException("Media timeline track must use mimeType=application/json");
        }
    }

    /**
     * MSF Section 8.2: Event Timeline Catalog requirements
     */
    private static void validateEventTimelineTrack(WarpTrack track) {
        if (isEmpty(track.getDepends())) {
            throw new IllegalArgumentException("Event timeline track must declare depends");
        }
        if (!"application/json".equals(track.getMimeType())) {
            throw new IllegalArgumentException("Event timeline track must use mimeType=application/json");
        }
        if (isBlank(track.getEventType())) {
            throw new IllegalArgumentException("Event timeline track must declare eventType");
        }
    }

    /**
     * CMSF Section 3.5: CMAF track catalog requirements.
     */
    private static void validateCmsfTrack(WarpTrack track) {
        if (isBlank(track.getInitData())) {
            throw new IllegalArgumentException("CMAF track must declare initData");
        }

        Integer maxGrpSapStartingType = track.getMaxGrpSapStartingType();
        if (maxGrpSapStartingType != null && maxGrpSapStartingType != 1 && maxGrpSapStartingType != 2) {
            throw new IllegalArgumentException("maxGrpSapStartingType must be 1 or 2");
        }

        Integer maxObjSapStartingType = track.getMaxObjSapStartingType();
        if (maxObjSapStartingType != null && (maxObjSapStartingType < 0 || maxObjSapStartingType > 3)) {
            throw new IllegalArgumentException("maxObjSapStartingType must be between 0 and 3");
        }
    }

    /**
     * MSF 5.1.16: All tracks in the same render group must have identical target latencies.
     */
    private static void validateRenderGroupLatency(List<WarpTrack> tracks) {
        Map<Integer, Long> renderGroupLatency = new HashMap<>();
        for (WarpTrack track : tracks) {
            if (track.getRenderGroup() != null && track.getTargetLatency() != null) {
                Long existing = renderGroupLatency.get(track.getRenderGroup());
                if (existing == null) {
                    renderGroupLatency.put(track.getRenderGroup(), track.getTargetLatency());
                } else if (!existing.equals(track.getTargetLatency())) {
                    throw new IllegalArgumentException(
                        "All tracks in render group " + track.getRenderGroup() +
                        " must have identical targetLatency (found " + existing + " and " + track.getTargetLatency() + ")");
                }
            }
        }
    }

    /**
     * MSF 5.1.16: All tracks in the same alternate group must have identical target latencies.
     */
    private static void validateAltGroupLatency(List<WarpTrack> tracks) {
        Map<Integer, Long> altGroupLatency = new HashMap<>();
        for (WarpTrack track : tracks) {
            if (track.getAltGroup() != null && track.getTargetLatency() != null) {
                Long existing = altGroupLatency.get(track.getAltGroup());
                if (existing == null) {
                    altGroupLatency.put(track.getAltGroup(), track.getTargetLatency());
                } else if (!existing.equals(track.getTargetLatency())) {
                    throw new IllegalArgumentException(
                        "All tracks in alternate group " + track.getAltGroup() +
                        " must have identical targetLatency (found " + existing + " and " + track.getTargetLatency() + ")");
                }
            }
        }
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
