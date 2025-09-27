package baritone.cache.seed;

import baritone.utils.io.VarIntUtils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Stores the column top heights using a base height plus 4-bit signed deltas.
 */
public final class PackedHeightField {

    private static final int COLUMN_COUNT = 16 * 16;

    private final int baseY;
    private final byte[] packed;
    private final boolean truncated;

    private PackedHeightField(int baseY, byte[] packed, boolean truncated) {
        this.baseY = baseY;
        this.packed = packed;
        this.truncated = truncated;
    }

    public int topY(int localX, int localZ) {
        int index = localZ * 16 + localX;
        int nibble = ((packed[index >> 1] >>> ((index & 1) * 4)) & 0xF);
        int delta = (nibble & 0x8) != 0 ? nibble - 0x10 : nibble;
        return baseY + delta;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VarIntUtils.writeZigZag(out, baseY);
        out.writeBytes(packed);
        out.write(truncated ? 1 : 0);
        return out.toByteArray();
    }

    public static PackedHeightField fromColumns(int[] columns) {
        if (columns.length != COLUMN_COUNT) {
            throw new IllegalArgumentException("Expected " + COLUMN_COUNT + " entries");
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : columns) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        int base = (min + max) / 2;
        byte[] packed = new byte[(COLUMN_COUNT + 1) / 2];
        boolean truncated = false;
        for (int i = 0; i < columns.length; i++) {
            int delta = columns[i] - base;
            if (delta < -8) {
                delta = -8;
                truncated = true;
            } else if (delta > 7) {
                delta = 7;
                truncated = true;
            }
            int nibble = delta & 0xF;
            int byteIndex = i >> 1;
            if ((i & 1) == 0) {
                packed[byteIndex] = (byte) (nibble & 0xF);
            } else {
                packed[byteIndex] |= (byte) (nibble << 4);
            }
        }
        return new PackedHeightField(base, packed, truncated);
    }

    public static PackedHeightField decode(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        int base = VarIntUtils.readZigZag(buffer);
        byte[] packed = new byte[(COLUMN_COUNT + 1) / 2];
        buffer.get(packed);
        boolean truncated = buffer.hasRemaining() && buffer.get() != 0;
        return new PackedHeightField(base, packed, truncated);
    }
}
