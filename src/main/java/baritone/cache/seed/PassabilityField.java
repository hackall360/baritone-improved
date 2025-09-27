package baritone.cache.seed;

import baritone.utils.io.VarIntUtils;

import java.io.ByteArrayOutputStream;
import java.util.BitSet;

/**
 * Compact run-length encoding for 16x16 passability masks.
 */
final class PassabilityField {

    private static final int CELL_COUNT = 16 * 16;

    private PassabilityField() {
    }

    static byte[] pack(BitSet bits) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean current = bits.get(0);
        int run = 1;
        for (int i = 1; i < CELL_COUNT; i++) {
            boolean value = bits.get(i);
            if (value == current) {
                run++;
            } else {
                writeRun(out, current, run);
                current = value;
                run = 1;
            }
        }
        writeRun(out, current, run);
        return out.toByteArray();
    }

    static BitSet unpack(byte[] data) {
        BitSet bits = new BitSet(CELL_COUNT);
        int index = 0;
        ByteBufferWrapper wrapper = new ByteBufferWrapper(data);
        while (wrapper.hasRemaining() && index < CELL_COUNT) {
            int encoded = wrapper.readUnsigned();
            boolean value = (encoded & 1) != 0;
            int run = encoded >>> 1;
            for (int i = 0; i < run && index < CELL_COUNT; i++, index++) {
                bits.set(index, value);
            }
        }
        return bits;
    }

    static boolean hasAnyPassable(byte[] packed) {
        ByteBufferWrapper wrapper = new ByteBufferWrapper(packed);
        while (wrapper.hasRemaining()) {
            int encoded = wrapper.readUnsigned();
            if ((encoded & 1) != 0 && encoded >>> 1 > 0) {
                return true;
            }
        }
        return false;
    }

    private static void writeRun(ByteArrayOutputStream out, boolean value, int length) {
        int encoded = (length << 1) | (value ? 1 : 0);
        VarIntUtils.writeUnsigned(out, encoded);
    }

    private static final class ByteBufferWrapper {
        private final byte[] data;
        private int index;

        private ByteBufferWrapper(byte[] data) {
            this.data = data;
        }

        int readUnsigned() {
            int value = 0;
            int shift = 0;
            while (true) {
                byte b = data[index++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
                shift += 7;
            }
        }

        boolean hasRemaining() {
            return index < data.length;
        }
    }
}
