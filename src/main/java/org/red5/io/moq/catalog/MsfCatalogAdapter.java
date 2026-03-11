package org.red5.io.moq.catalog;

import org.red5.io.moq.msf.catalog.MsfCatalog;
import org.red5.io.moq.msf.catalog.MsfTrack;
import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts between the shared catalog model and the MSF catalog model.
 *
 * This adapter reuses the WARP adapter for the currently overlapping subset
 * and fails loudly for MSF-specific fields that the shared catalog model does
 * not represent yet.
 */
public final class MsfCatalogAdapter {

    public static final int STREAMING_FORMAT_MSF = 101;
    public static final String STREAMING_FORMAT_VERSION_MSF = "msf-00";

    private MsfCatalogAdapter() {
    }

    public static Catalog toCatalog(MsfCatalog msfCatalog) {
        if (msfCatalog == null) {
            throw new IllegalArgumentException("MsfCatalog cannot be null");
        }

        Catalog catalog = WarpCatalogAdapter.toCatalog(msfCatalog);
        catalog.setStreamingFormat(STREAMING_FORMAT_MSF);
        catalog.setStreamingFormatVersion(STREAMING_FORMAT_VERSION_MSF);
        return catalog;
    }

    public static MsfCatalog toMsfCatalog(Catalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Catalog cannot be null");
        }

        var warpCatalog = WarpCatalogAdapter.toWarpCatalog(catalog);
        MsfCatalog msfCatalog = new MsfCatalog();
        msfCatalog.setVersion(warpCatalog.getVersion());
        msfCatalog.setDeltaUpdate(warpCatalog.getDeltaUpdate());
        msfCatalog.setIsComplete(warpCatalog.getIsComplete());
        msfCatalog.setGeneratedAt(warpCatalog.getGeneratedAt());
        msfCatalog.setTracks(warpCatalog.getTracks());
        msfCatalog.setAddTracks(warpCatalog.getAddTracks());
        msfCatalog.setRemoveTracks(warpCatalog.getRemoveTracks());
        msfCatalog.setCloneTracks(warpCatalog.getCloneTracks());
        return msfCatalog;
    }

    public static MsfTrack toMsfTrack(CatalogTrack track, CommonTrackFields commonTrackFields) {
        WarpTrack warpTrack = WarpCatalogAdapter.toWarpCatalog(wrapTrack(track, commonTrackFields)).getTracks().getFirst();
        MsfTrack msfTrack = new MsfTrack();
        msfTrack.setNamespace(warpTrack.getNamespace());
        msfTrack.setName(warpTrack.getName());
        msfTrack.setPackaging(warpTrack.getPackaging());
        msfTrack.setLabel(warpTrack.getLabel());
        msfTrack.setRenderGroup(warpTrack.getRenderGroup());
        msfTrack.setAltGroup(warpTrack.getAltGroup());
        msfTrack.setInitData(warpTrack.getInitData());
        msfTrack.setDepends(warpTrack.getDepends());
        msfTrack.setTemporalId(warpTrack.getTemporalId());
        msfTrack.setSpatialId(warpTrack.getSpatialId());
        msfTrack.setCodec(warpTrack.getCodec());
        msfTrack.setMimeType(warpTrack.getMimeType());
        msfTrack.setFramerate(warpTrack.getFramerate());
        msfTrack.setBitrate(warpTrack.getBitrate());
        msfTrack.setWidth(warpTrack.getWidth());
        msfTrack.setHeight(warpTrack.getHeight());
        msfTrack.setSamplerate(warpTrack.getSamplerate());
        msfTrack.setChannelConfig(warpTrack.getChannelConfig());
        msfTrack.setDisplayWidth(warpTrack.getDisplayWidth());
        msfTrack.setDisplayHeight(warpTrack.getDisplayHeight());
        msfTrack.setLang(warpTrack.getLang());
        return msfTrack;
    }

    private static Catalog wrapTrack(CatalogTrack track, CommonTrackFields commonTrackFields) {
        Catalog catalog = new Catalog(STREAMING_FORMAT_MSF, STREAMING_FORMAT_VERSION_MSF);
        catalog.setCommonTrackFields(commonTrackFields);
        catalog.setTracks(List.of(track));
        return catalog;
    }
}
