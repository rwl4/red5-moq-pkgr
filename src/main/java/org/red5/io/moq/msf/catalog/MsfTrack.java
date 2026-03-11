package org.red5.io.moq.msf.catalog;

import org.red5.io.moq.warp.catalog.WarpTrack;

import java.util.Base64;
import java.util.ArrayList;
import java.util.List;

/**
 * MSF track with builder pattern for easier construction.
 * Extends WarpTrack with MSF-specific convenience methods.
 */
public class MsfTrack extends WarpTrack {

    /**
     * Create a new MSF track builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder for a LOC-packaged video track.
     */
    public static Builder video(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.LOC)
            .role(TrackRole.VIDEO);
    }

    /**
     * Create a builder for a LOC-packaged audio track.
     */
    public static Builder audio(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.LOC)
            .role(TrackRole.AUDIO);
    }

    /**
     * Create a builder for a CMAF-packaged video track.
     */
    public static Builder cmafVideo(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.CMAF)
            .role(TrackRole.VIDEO);
    }

    /**
     * Create a builder for a CMAF-packaged audio track.
     */
    public static Builder cmafAudio(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.CMAF)
            .role(TrackRole.AUDIO);
    }

    /**
     * Create a builder for a media timeline track.
     */
    public static Builder mediaTimeline(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.MEDIA_TIMELINE)
            .role(TrackRole.MEDIA_TIMELINE)
            .mimeType(MsfConstants.JSON_MIME_TYPE);
    }

    /**
     * Create a builder for an event timeline track.
     */
    public static Builder eventTimeline(String name, String eventType) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.EVENT_TIMELINE)
            .role(TrackRole.EVENT_TIMELINE)
            .mimeType(MsfConstants.JSON_MIME_TYPE)
            .eventType(eventType);
    }

    /**
     * Create a builder for a caption track.
     */
    public static Builder caption(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.LOC)
            .role(TrackRole.CAPTION);
    }

    /**
     * Create a builder for a subtitle track.
     */
    public static Builder subtitle(String name) {
        return new Builder()
            .name(name)
            .packaging(PackagingType.LOC)
            .role(TrackRole.SUBTITLE);
    }

    /**
     * Builder for constructing MSF tracks.
     */
    public static class Builder {
        private final MsfTrack track;
        private List<String> dependencies;

        private Builder() {
            this.track = new MsfTrack();
            this.dependencies = new ArrayList<>();
        }

        public Builder name(String name) {
            track.setName(name);
            return this;
        }

        public Builder namespace(String namespace) {
            track.setNamespace(namespace);
            return this;
        }

        public Builder packaging(PackagingType type) {
            track.setPackaging(type.getValue());
            return this;
        }

        public Builder packaging(String packaging) {
            track.setPackaging(packaging);
            return this;
        }

        public Builder role(TrackRole role) {
            track.setRole(role.getValue());
            return this;
        }

        public Builder role(String role) {
            track.setRole(role);
            return this;
        }

        public Builder label(String label) {
            track.setLabel(label);
            return this;
        }

        public Builder live(boolean isLive) {
            track.setIsLive(isLive);
            return this;
        }

        public Builder live() {
            return live(true);
        }

        public Builder vod() {
            return live(false);
        }

        public Builder targetLatency(long milliseconds) {
            track.setTargetLatency(milliseconds);
            return this;
        }

        public Builder trackDuration(long milliseconds) {
            track.setTrackDuration(milliseconds);
            return this;
        }

        public Builder renderGroup(int group) {
            track.setRenderGroup(group);
            return this;
        }

        public Builder altGroup(int group) {
            track.setAltGroup(group);
            return this;
        }

        public Builder codec(String codec) {
            track.setCodec(codec);
            return this;
        }

        public Builder mimeType(String mimeType) {
            track.setMimeType(mimeType);
            return this;
        }

        public Builder eventType(String eventType) {
            track.setEventType(eventType);
            return this;
        }

        public Builder initData(String base64InitData) {
            track.setInitData(base64InitData);
            return this;
        }

        /**
         * Set initialization data from raw bytes, encoded as base64 for catalog transport.
         */
        public Builder initDataBytes(byte[] initData) {
            track.setInitData(initData == null ? null : Base64.getEncoder().encodeToString(initData));
            return this;
        }

        public Builder dependsOn(String trackName) {
            dependencies.add(trackName);
            return this;
        }

        public Builder dependsOn(List<String> trackNames) {
            dependencies.addAll(trackNames);
            return this;
        }

        public Builder temporalId(int id) {
            track.setTemporalId(id);
            return this;
        }

        public Builder spatialId(int id) {
            track.setSpatialId(id);
            return this;
        }

        public Builder framerate(int fps) {
            track.setFramerate(fps);
            return this;
        }

        public Builder timescale(int timescale) {
            track.setTimescale(timescale);
            return this;
        }

        public Builder bitrate(int bitsPerSecond) {
            track.setBitrate(bitsPerSecond);
            return this;
        }

        public Builder resolution(int width, int height) {
            track.setWidth(width);
            track.setHeight(height);
            return this;
        }

        public Builder displayResolution(int width, int height) {
            track.setDisplayWidth(width);
            track.setDisplayHeight(height);
            return this;
        }

        public Builder sampleRate(int hz) {
            track.setSamplerate(hz);
            return this;
        }

        public Builder channelConfig(String config) {
            track.setChannelConfig(config);
            return this;
        }

        public Builder language(String lang) {
            track.setLang(lang);
            return this;
        }

        public Builder maxGrpSapStartingType(int sapType) {
            track.setMaxGrpSapStartingType(sapType);
            return this;
        }

        public Builder maxObjSapStartingType(int sapType) {
            track.setMaxObjSapStartingType(sapType);
            return this;
        }

        /**
         * Build the track.
         */
        public MsfTrack build() {
            if (!dependencies.isEmpty()) {
                track.setDepends(new ArrayList<>(dependencies));
            }
            return track;
        }
    }
}
