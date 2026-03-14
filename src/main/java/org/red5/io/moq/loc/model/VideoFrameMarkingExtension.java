package org.red5.io.moq.loc.model;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Video Frame Marking LOC Header Extension.
 *
 * Flags for video frames which are independent, discardable, or base layer sync points,
 * as well as temporal and spatial layer identification, as defined in RFC9626.
 *
 * ID: 4
 * Value Type: Varint (even ID)
 * Length: Varies (1-4 bytes)
 *
 * Bit layout per RFC 9626 (first byte of varint value):
 * - Bit 7: S (Start of frame) — not used by LOC, reserved
 * - Bit 6: E (End of frame) — not used by LOC, reserved
 * - Bit 5: I (Independent frame)
 * - Bit 4: D (Discardable frame)
 * - Bit 3: B (Base layer sync point)
 * - Bits 2-0: TID (Temporal Layer ID, 0-7)
 *
 * If B=1 and second byte present:
 * - Bits 15-10: LID (Layer ID, 0-63)
 * - Bits 9-8: Reserved
 *
 * Reference: draft-ietf-moq-loc Section 2.3.2.2
 *            RFC9626
 */
public class VideoFrameMarkingExtension extends LocHeaderExtension {

    public static final int EXTENSION_ID = 4;

    private boolean independent;
    private boolean discardable;
    private boolean baseLayerSync;
    private int temporalLayerId; // 0-7
    private int spatialLayerId;  // 0-3

    public VideoFrameMarkingExtension() {
        super(EXTENSION_ID);
    }

    public VideoFrameMarkingExtension(boolean independent, boolean discardable,
                                     boolean baseLayerSync, int temporalLayerId, int spatialLayerId) {
        super(EXTENSION_ID);
        this.independent = independent;
        this.discardable = discardable;
        this.baseLayerSync = baseLayerSync;
        setTemporalLayerId(temporalLayerId);
        setSpatialLayerId(spatialLayerId);
    }

    public boolean isIndependent() {
        return independent;
    }

    public void setIndependent(boolean independent) {
        this.independent = independent;
    }

    public boolean isDiscardable() {
        return discardable;
    }

    public void setDiscardable(boolean discardable) {
        this.discardable = discardable;
    }

    public boolean isBaseLayerSync() {
        return baseLayerSync;
    }

    public void setBaseLayerSync(boolean baseLayerSync) {
        this.baseLayerSync = baseLayerSync;
    }

    public int getTemporalLayerId() {
        return temporalLayerId;
    }

    public void setTemporalLayerId(int temporalLayerId) {
        if (temporalLayerId < 0 || temporalLayerId > 7) {
            throw new IllegalArgumentException("Temporal Layer ID must be 0-7");
        }
        this.temporalLayerId = temporalLayerId;
    }

    public int getSpatialLayerId() {
        return spatialLayerId;
    }

    public void setSpatialLayerId(int spatialLayerId) {
        if (spatialLayerId < 0 || spatialLayerId > 3) {
            throw new IllegalArgumentException("Spatial Layer ID must be 0-3");
        }
        this.spatialLayerId = spatialLayerId;
    }

    @Override
    protected byte[] serializeValue() throws IOException {
        // RFC 9626 bit layout: S(7) E(6) I(5) D(4) B(3) TID(2-0)
        long value = 0;

        if (independent) value |= 0x20;   // bit 5
        if (discardable) value |= 0x10;   // bit 4
        if (baseLayerSync) value |= 0x08; // bit 3
        value |= temporalLayerId & 0x07;  // bits 2-0

        if (baseLayerSync && spatialLayerId > 0) {
            // Second byte: LID(7-2) Reserved(1-0)
            long secondByte = (spatialLayerId & 0x3F) << 2;
            value = (value << 8) | secondByte;
        }

        return serializeVarint(value);
    }

    @Override
    public void deserializeValue(ByteBuffer buffer, int length) throws IOException {
        long value = readVarint(buffer);

        // RFC 9626 bit layout: S(7) E(6) I(5) D(4) B(3) TID(2-0)
        // Two-byte form when value >= 256: first byte has flags, second byte has LID
        boolean hasTwoBytes = value >= 256;
        long firstByte = hasTwoBytes ? (value >> 8) & 0xFF : value & 0xFF;

        this.independent = (firstByte & 0x20) != 0;   // bit 5
        this.discardable = (firstByte & 0x10) != 0;    // bit 4
        this.baseLayerSync = (firstByte & 0x08) != 0;  // bit 3
        this.temporalLayerId = (int) (firstByte & 0x07); // bits 2-0

        if (hasTwoBytes) {
            this.spatialLayerId = (int) ((value & 0xFF) >> 2) & 0x3F;
        } else {
            this.spatialLayerId = 0;
        }
    }

    @Override
    public String toString() {
        return "VideoFrameMarkingExtension{" +
                "independent=" + independent +
                ", discardable=" + discardable +
                ", baseLayerSync=" + baseLayerSync +
                ", temporalLayerId=" + temporalLayerId +
                ", spatialLayerId=" + spatialLayerId +
                '}';
    }
}
