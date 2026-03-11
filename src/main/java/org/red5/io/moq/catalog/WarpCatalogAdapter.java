package org.red5.io.moq.catalog;

import org.red5.io.moq.warp.catalog.WarpCatalog;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the shared catalog model and the WARP catalog model.
 *
 * This adapter is intentionally strict: if conversion would drop WARP-only
 * fields that the shared catalog model does not represent yet, it fails.
 */
public final class WarpCatalogAdapter {

    public static final int STREAMING_FORMAT_WARP = 100;
    public static final String STREAMING_FORMAT_VERSION_WARP = "warp-01";

    private WarpCatalogAdapter() {
    }

    public static Catalog toCatalog(WarpCatalog warpCatalog) {
        if (warpCatalog == null) {
            throw new IllegalArgumentException("WarpCatalog cannot be null");
        }

        Catalog catalog = new Catalog(STREAMING_FORMAT_WARP, STREAMING_FORMAT_VERSION_WARP);
        if (warpCatalog.getVersion() != null) {
            catalog.setVersion(warpCatalog.getVersion());
        }
        catalog.setDeltaUpdate(warpCatalog.getDeltaUpdate());
        catalog.setIsComplete(warpCatalog.getIsComplete());
        catalog.setGeneratedAt(warpCatalog.getGeneratedAt());
        if (warpCatalog.getAddTracks() != null) {
            catalog.setAddTracks(toCatalogTracks(warpCatalog.getAddTracks(), null));
        }
        if (warpCatalog.getRemoveTracks() != null) {
            catalog.setRemoveTracks(toCatalogTracks(warpCatalog.getRemoveTracks(), null));
        }
        if (warpCatalog.getCloneTracks() != null) {
            catalog.setCloneTracks(toCatalogTracks(warpCatalog.getCloneTracks(), null));
        }

        List<WarpTrack> warpTracks = warpCatalog.getTracks();
        if (warpTracks != null && !warpTracks.isEmpty()) {
            CommonTrackFields commonTrackFields = buildCommonTrackFields(warpTracks);
            if (commonTrackFields != null) {
                catalog.setCommonTrackFields(commonTrackFields);
            }
            catalog.setTracks(toCatalogTracks(warpTracks, commonTrackFields));
        }
        return catalog;
    }

    public static WarpCatalog toWarpCatalog(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }
        if (catalog.getCatalogs() != null && !catalog.getCatalogs().isEmpty()) {
            throw new IllegalArgumentException("Nested catalogs are not supported in WarpCatalog conversion");
        }
        if (catalog.getTracks() == null || catalog.getTracks().isEmpty()) {
            throw new IllegalArgumentException("Catalog must contain tracks for conversion");
        }

        WarpCatalog warpCatalog = new WarpCatalog();
        warpCatalog.setVersion(catalog.getVersion());
        warpCatalog.setDeltaUpdate(catalog.getDeltaUpdate());
        warpCatalog.setIsComplete(catalog.getIsComplete());
        warpCatalog.setGeneratedAt(catalog.getGeneratedAt());

        if (catalog.getTracks() != null) {
            List<WarpTrack> tracks = new ArrayList<>(catalog.getTracks().size());
            for (CatalogTrack track : catalog.getTracks()) {
                tracks.add(toWarpTrack(track, catalog.getCommonTrackFields()));
            }
            warpCatalog.setTracks(tracks);
        }
        if (catalog.getAddTracks() != null) {
            warpCatalog.setAddTracks(toWarpTracks(catalog.getAddTracks(), catalog.getCommonTrackFields()));
        }
        if (catalog.getRemoveTracks() != null) {
            warpCatalog.setRemoveTracks(toWarpTracks(catalog.getRemoveTracks(), catalog.getCommonTrackFields()));
        }
        if (catalog.getCloneTracks() != null) {
            warpCatalog.setCloneTracks(toWarpTracks(catalog.getCloneTracks(), catalog.getCommonTrackFields()));
        }
        return warpCatalog;
    }

    private static CatalogTrack toCatalogTrack(WarpTrack warpTrack, CommonTrackFields commonTrackFields) {
        if (warpTrack == null) {
            throw new IllegalArgumentException("WarpTrack cannot be null");
        }

        CatalogTrack track = new CatalogTrack();
        track.setName(warpTrack.getName());
        if (!inherits(commonTrackFields == null ? null : commonTrackFields.getNamespace(), warpTrack.getNamespace())) {
            track.setNamespace(warpTrack.getNamespace());
        }
        if (!inherits(commonTrackFields == null ? null : commonTrackFields.getPackaging(), warpTrack.getPackaging())) {
            track.setPackaging(warpTrack.getPackaging());
        }
        if (!inherits(commonTrackFields == null ? null : commonTrackFields.getRenderGroup(), warpTrack.getRenderGroup())) {
            track.setRenderGroup(warpTrack.getRenderGroup());
        }

        track.setLabel(warpTrack.getLabel());
        track.setAltGroup(warpTrack.getAltGroup());
        track.setInitData(warpTrack.getInitData());
        track.setDepends(warpTrack.getDepends());
        track.setTemporalId(warpTrack.getTemporalId());
        track.setSpatialId(warpTrack.getSpatialId());
        track.setRole(warpTrack.getRole());
        track.setParentName(warpTrack.getParentName());
        track.setTrackDuration(warpTrack.getTrackDuration());
        track.setIsLive(warpTrack.getIsLive());
        track.setTargetLatency(warpTrack.getTargetLatency());
        track.setType(warpTrack.getType());
        track.setEventType(warpTrack.getEventType());
        track.setMaxGrpSapStartingType(warpTrack.getMaxGrpSapStartingType());
        track.setMaxObjSapStartingType(warpTrack.getMaxObjSapStartingType());

        SelectionParameters selectionParams = buildSelectionParameters(warpTrack);
        if (selectionParams != null) {
            track.setSelectionParams(selectionParams);
        }

        return track;
    }

    private static WarpTrack toWarpTrack(CatalogTrack track, CommonTrackFields commonTrackFields) {
        if (track == null) {
            throw new IllegalArgumentException("CatalogTrack cannot be null");
        }
        WarpTrack warpTrack = new WarpTrack();
        warpTrack.setName(track.getName());
        warpTrack.setNamespace(firstNonNull(track.getNamespace(), commonTrackFields == null ? null : commonTrackFields.getNamespace()));
        warpTrack.setPackaging(firstNonNull(track.getPackaging(), commonTrackFields == null ? null : commonTrackFields.getPackaging()));
        warpTrack.setRenderGroup(firstNonNull(track.getRenderGroup(), commonTrackFields == null ? null : commonTrackFields.getRenderGroup()));
        warpTrack.setLabel(track.getLabel());
        warpTrack.setAltGroup(track.getAltGroup());
        warpTrack.setInitData(track.getInitData());
        warpTrack.setDepends(track.getDepends());
        warpTrack.setTemporalId(track.getTemporalId());
        warpTrack.setSpatialId(track.getSpatialId());
        warpTrack.setRole(track.getRole());
        warpTrack.setParentName(track.getParentName());
        warpTrack.setTrackDuration(track.getTrackDuration());
        warpTrack.setIsLive(track.getIsLive());
        warpTrack.setTargetLatency(track.getTargetLatency());
        warpTrack.setType(track.getType());
        warpTrack.setEventType(track.getEventType());
        warpTrack.setMaxGrpSapStartingType(track.getMaxGrpSapStartingType());
        warpTrack.setMaxObjSapStartingType(track.getMaxObjSapStartingType());

        SelectionParameters selectionParams = mergeSelectionParams(commonTrackFields, track);
        if (selectionParams != null) {
            if (selectionParams.getCodec() != null) warpTrack.setCodec(selectionParams.getCodec());
            if (selectionParams.getMimeType() != null) warpTrack.setMimeType(selectionParams.getMimeType());
            if (selectionParams.getFramerate() != null) warpTrack.setFramerate(selectionParams.getFramerate().intValue());
            if (selectionParams.getTimescale() != null) warpTrack.setTimescale(selectionParams.getTimescale());
            if (selectionParams.getBitrate() != null) warpTrack.setBitrate(selectionParams.getBitrate().intValue());
            if (selectionParams.getWidth() != null) warpTrack.setWidth(selectionParams.getWidth());
            if (selectionParams.getHeight() != null) warpTrack.setHeight(selectionParams.getHeight());
            if (selectionParams.getSamplerate() != null) warpTrack.setSamplerate(selectionParams.getSamplerate());
            if (selectionParams.getChannelConfig() != null) warpTrack.setChannelConfig(selectionParams.getChannelConfig());
            if (selectionParams.getDisplayWidth() != null) warpTrack.setDisplayWidth(selectionParams.getDisplayWidth());
            if (selectionParams.getDisplayHeight() != null) warpTrack.setDisplayHeight(selectionParams.getDisplayHeight());
            if (selectionParams.getLang() != null) warpTrack.setLang(selectionParams.getLang());
        }
        return warpTrack;
    }

    private static CommonTrackFields buildCommonTrackFields(List<WarpTrack> tracks) {
        String namespace = commonString(tracks, WarpTrack::getNamespace);
        String packaging = commonString(tracks, WarpTrack::getPackaging);
        Integer renderGroup = commonInteger(tracks, WarpTrack::getRenderGroup);

        if (namespace == null && packaging == null && renderGroup == null) {
            return null;
        }

        CommonTrackFields common = new CommonTrackFields();
        common.setNamespace(namespace);
        common.setPackaging(packaging);
        common.setRenderGroup(renderGroup);
        return common;
    }

    private static SelectionParameters buildSelectionParameters(WarpTrack track) {
        SelectionParameters selectionParams = new SelectionParameters();
        boolean present = false;
        if (track.getCodec() != null) {
            selectionParams.setCodec(track.getCodec());
            present = true;
        }
        if (track.getMimeType() != null) {
            selectionParams.setMimeType(track.getMimeType());
            present = true;
        }
        if (track.getFramerate() != null) {
            selectionParams.setFramerate(track.getFramerate().doubleValue());
            present = true;
        }
        if (track.getTimescale() != null) {
            selectionParams.setTimescale(track.getTimescale());
            present = true;
        }
        if (track.getBitrate() != null) {
            selectionParams.setBitrate(track.getBitrate().longValue());
            present = true;
        }
        if (track.getWidth() != null) {
            selectionParams.setWidth(track.getWidth());
            present = true;
        }
        if (track.getHeight() != null) {
            selectionParams.setHeight(track.getHeight());
            present = true;
        }
        if (track.getSamplerate() != null) {
            selectionParams.setSamplerate(track.getSamplerate());
            present = true;
        }
        if (track.getChannelConfig() != null) {
            selectionParams.setChannelConfig(track.getChannelConfig());
            present = true;
        }
        if (track.getDisplayWidth() != null) {
            selectionParams.setDisplayWidth(track.getDisplayWidth());
            present = true;
        }
        if (track.getDisplayHeight() != null) {
            selectionParams.setDisplayHeight(track.getDisplayHeight());
            present = true;
        }
        if (track.getLang() != null) {
            selectionParams.setLang(track.getLang());
            present = true;
        }
        return present ? selectionParams : null;
    }

    private static SelectionParameters mergeSelectionParams(CommonTrackFields commonTrackFields, CatalogTrack track) {
        SelectionParameters merged = new SelectionParameters();
        boolean present = false;

        SelectionParameters common = commonTrackFields == null ? null : commonTrackFields.getSelectionParams();
        SelectionParameters specific = track.getSelectionParams();

        String codec = firstNonNull(specific == null ? null : specific.getCodec(), common == null ? null : common.getCodec());
        String mimeType = firstNonNull(specific == null ? null : specific.getMimeType(), common == null ? null : common.getMimeType());
        Double framerate = firstNonNull(specific == null ? null : specific.getFramerate(), common == null ? null : common.getFramerate());
        Integer timescale = firstNonNull(specific == null ? null : specific.getTimescale(), common == null ? null : common.getTimescale());
        Long bitrate = firstNonNull(specific == null ? null : specific.getBitrate(), common == null ? null : common.getBitrate());
        Integer width = firstNonNull(specific == null ? null : specific.getWidth(), common == null ? null : common.getWidth());
        Integer height = firstNonNull(specific == null ? null : specific.getHeight(), common == null ? null : common.getHeight());
        Integer samplerate = firstNonNull(specific == null ? null : specific.getSamplerate(), common == null ? null : common.getSamplerate());
        String channelConfig = firstNonNull(specific == null ? null : specific.getChannelConfig(), common == null ? null : common.getChannelConfig());
        Integer displayWidth = firstNonNull(specific == null ? null : specific.getDisplayWidth(), common == null ? null : common.getDisplayWidth());
        Integer displayHeight = firstNonNull(specific == null ? null : specific.getDisplayHeight(), common == null ? null : common.getDisplayHeight());
        String lang = firstNonNull(specific == null ? null : specific.getLang(), common == null ? null : common.getLang());

        if (codec != null) {
            merged.setCodec(codec);
            present = true;
        }
        if (mimeType != null) {
            merged.setMimeType(mimeType);
            present = true;
        }
        if (framerate != null) {
            merged.setFramerate(framerate);
            present = true;
        }
        if (timescale != null) {
            merged.setTimescale(timescale);
            present = true;
        }
        if (bitrate != null) {
            merged.setBitrate(bitrate);
            present = true;
        }
        if (width != null) {
            merged.setWidth(width);
            present = true;
        }
        if (height != null) {
            merged.setHeight(height);
            present = true;
        }
        if (samplerate != null) {
            merged.setSamplerate(samplerate);
            present = true;
        }
        if (channelConfig != null) {
            merged.setChannelConfig(channelConfig);
            present = true;
        }
        if (displayWidth != null) {
            merged.setDisplayWidth(displayWidth);
            present = true;
        }
        if (displayHeight != null) {
            merged.setDisplayHeight(displayHeight);
            present = true;
        }
        if (lang != null) {
            merged.setLang(lang);
            present = true;
        }

        return present ? merged : null;
    }

    private static boolean inherits(Object commonValue, Object trackValue) {
        return commonValue != null && commonValue.equals(trackValue);
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static String commonString(List<WarpTrack> tracks, TrackStringExtractor extractor) {
        String candidate = tracks.getFirst() == null ? null : extractor.get(tracks.getFirst());
        if (candidate == null) {
            return null;
        }
        for (WarpTrack track : tracks) {
            if (!candidate.equals(extractor.get(track))) {
                return null;
            }
        }
        return candidate;
    }

    private static Integer commonInteger(List<WarpTrack> tracks, TrackIntegerExtractor extractor) {
        Integer candidate = tracks.getFirst() == null ? null : extractor.get(tracks.getFirst());
        if (candidate == null) {
            return null;
        }
        for (WarpTrack track : tracks) {
            if (!candidate.equals(extractor.get(track))) {
                return null;
            }
        }
        return candidate;
    }

    @FunctionalInterface
    private interface TrackStringExtractor {
        String get(WarpTrack track);
    }

    @FunctionalInterface
    private interface TrackIntegerExtractor {
        Integer get(WarpTrack track);
    }

    private static List<CatalogTrack> toCatalogTracks(List<WarpTrack> warpTracks, CommonTrackFields commonTrackFields) {
        List<CatalogTrack> tracks = new ArrayList<>(warpTracks.size());
        for (WarpTrack warpTrack : warpTracks) {
            tracks.add(toCatalogTrack(warpTrack, commonTrackFields));
        }
        return tracks;
    }

    private static List<WarpTrack> toWarpTracks(List<CatalogTrack> catalogTracks, CommonTrackFields commonTrackFields) {
        List<WarpTrack> tracks = new ArrayList<>(catalogTracks.size());
        for (CatalogTrack catalogTrack : catalogTracks) {
            tracks.add(toWarpTrack(catalogTrack, commonTrackFields));
        }
        return tracks;
    }
}
