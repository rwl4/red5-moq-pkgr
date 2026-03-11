package org.red5.io.moq.catalog;

/**
 * Selection Parameters for track selection.
 *
 * These parameters help subscribers select which tracks to subscribe to
 * based on codec, resolution, bitrate, and other media properties.
 */
public class SelectionParameters {

    /** Codec identifier (e.g., "av01", "opus", "avc1") */
    private String codec;

    /** MIME type */
    private String mimeType;

    /** Framerate in frames per second */
    private Double framerate;

    /** Bitrate in bits per second */
    private Long bitrate;

    /** Encoded width in pixels */
    private Integer width;

    /** Encoded height in pixels */
    private Integer height;

    /** Audio sample rate in Hz */
    private Integer samplerate;

    /** Audio channel configuration (e.g., "2" for stereo) */
    private String channelConfig;

    /** Display width in pixels */
    private Integer displayWidth;

    /** Display height in pixels */
    private Integer displayHeight;

    /** Language tag (RFC 5646) */
    private String lang;

    public SelectionParameters() {
    }

    // Getters and setters

    public String getCodec() {
        return codec;
    }

    public void setCodec(String codec) {
        this.codec = codec;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Double getFramerate() {
        return framerate;
    }

    public void setFramerate(Double framerate) {
        this.framerate = framerate;
    }

    public Long getBitrate() {
        return bitrate;
    }

    public void setBitrate(Long bitrate) {
        this.bitrate = bitrate;
    }

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public Integer getSamplerate() {
        return samplerate;
    }

    public void setSamplerate(Integer samplerate) {
        this.samplerate = samplerate;
    }

    public String getChannelConfig() {
        return channelConfig;
    }

    public void setChannelConfig(String channelConfig) {
        this.channelConfig = channelConfig;
    }

    public Integer getDisplayWidth() {
        return displayWidth;
    }

    public void setDisplayWidth(Integer displayWidth) {
        this.displayWidth = displayWidth;
    }

    public Integer getDisplayHeight() {
        return displayHeight;
    }

    public void setDisplayHeight(Integer displayHeight) {
        this.displayHeight = displayHeight;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SelectionParameters{");
        if (codec != null) sb.append("codec='").append(codec).append('\'');
        if (width != null) sb.append(", width=").append(width);
        if (height != null) sb.append(", height=").append(height);
        if (framerate != null) sb.append(", framerate=").append(framerate);
        if (bitrate != null) sb.append(", bitrate=").append(bitrate);
        if (samplerate != null) sb.append(", samplerate=").append(samplerate);
        if (channelConfig != null) sb.append(", channelConfig='").append(channelConfig).append('\'');
        if (lang != null) sb.append(", lang='").append(lang).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
