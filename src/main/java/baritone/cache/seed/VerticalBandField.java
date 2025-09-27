package baritone.cache.seed;

import baritone.utils.io.VarIntUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Stores 8-bit band occupancy per column for subsurface summaries (caves, ores).
 */
public final class VerticalBandField {

    private static final int COLUMN_COUNT = 16 * 16;

    private final byte bandsPerColumn;
    private final byte[] values;

    private VerticalBandField(byte bandsPerColumn, byte[] values) {
        this.bandsPerColumn = bandsPerColumn;
        this.values = values;
    }

    public byte bandsPerColumn() {
        return bandsPerColumn;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bandsPerColumn);
        VarIntUtils.writeUnsigned(out, values.length);
        out.writeBytes(values);
        return out.toByteArray();
    }

    public byte bandValue(int localX, int localZ, int band) {
        int index = ((localZ * 16) + localX) * bandsPerColumn + band;
        return values[index];
    }

    public boolean hasAny() {
        for (byte value : values) {
            if (value != 0) {
                return true;
            }
        }
        return false;
    }

    public static VerticalBandField decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        byte bands = buffer.get();
        int length = VarIntUtils.readUnsigned(buffer);
        byte[] values = new byte[length];
        buffer.get(values);
        return new VerticalBandField(bands, values);
    }

    public static byte[] encode(byte bandsPerColumn, byte[] values) {
        return new VerticalBandField(bandsPerColumn, values).toByteArray();
    }

    static boolean hasAnyEncoded(byte[] encoded) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        buffer.get();
        int length = VarIntUtils.readUnsigned(buffer);
        for (int i = 0; i < length; i++) {
            if (buffer.get() != 0) {
                return true;
            }
        }
        return false;
    }
}
