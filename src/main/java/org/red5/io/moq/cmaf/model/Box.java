package org.red5.io.moq.cmaf.model;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Base class for all ISO BMFF boxes.
 * ISO Base Media File Format (ISO/IEC 14496-12) defines a container format for media data.
 */
public abstract class Box {
    protected long size;
    protected String type;
    protected byte[] extendedType; // For UUID type boxes

    public Box(String type) {
        this.type = type;
    }

    public Box(String type, long size) {
        this.type = type;
        this.size = size;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public byte[] getExtendedType() {
        return extendedType;
    }

    public void setExtendedType(byte[] extendedType) {
        this.extendedType = extendedType;
    }

    /**
     * Serialize this box to a byte array.
     * @return byte array representation of the box
     */
    public abstract byte[] serialize() throws IOException;

    /**
     * Deserialize from a ByteBuffer.
     * @param buffer ByteBuffer containing box data
     */
    public abstract void deserialize(ByteBuffer buffer) throws IOException;

    /**
     * Write box header (size and type).
     * @param buffer ByteBuffer to write to
     */
    protected void writeHeader(ByteBuffer buffer) {
        buffer.putInt((int) size);
        buffer.put(type.getBytes());
    }

    /**
     * Read box header (size and type).
     * @param buffer ByteBuffer to read from
     * @return actual box size
     */
    protected long readHeader(ByteBuffer buffer) {
        this.size = Integer.toUnsignedLong(buffer.getInt());
        byte[] typeBytes = new byte[4];
        buffer.get(typeBytes);
        this.type = new String(typeBytes);

        // Handle 64-bit size
        if (this.size == 1) {
            this.size = buffer.getLong();
        }

        return this.size;
    }

    /**
     * Calculate the size of the box.
     * @return calculated size
     */
    protected abstract long calculateSize();

    /**
     * Write this box to an OutputStream.
     * @param out OutputStream to write to
     */
    public void write(OutputStream out) throws IOException {
        out.write(serialize());
    }

    @Override
    public String toString() {
        return "Box{type='" + type + "', size=" + size + "}";
    }
}
