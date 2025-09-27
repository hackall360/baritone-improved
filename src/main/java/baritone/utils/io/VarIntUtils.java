package baritone.utils.io;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Utility helpers for encoding and decoding little-endian style base-128 varints.
 * This is tailored for compact chunk blobs that need to be parsed from {@link ByteBuffer}s
 * without allocations.
 */
public final class VarIntUtils {

    private VarIntUtils() {
    }

    public static void writeUnsigned(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & 0xFFFFFF80) != 0L) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v & 0x7F);
    }

    public static void writeZigZag(ByteArrayOutputStream out, int value) {
        writeUnsigned(out, encodeZigZag32(value));
    }

    public static int readUnsigned(ByteBuffer buffer) {
        int value = 0;
        int shift = 0;
        while (true) {
            byte b = buffer.get();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift >= 35) {
                throw new IllegalArgumentException("VarInt too large");
            }
        }
    }

    public static int readZigZag(ByteBuffer buffer) {
        return decodeZigZag32(readUnsigned(buffer));
    }

    public static int encodeZigZag32(int value) {
        return (value << 1) ^ (value >> 31);
    }

    public static int decodeZigZag32(int value) {
        return (value >>> 1) ^ -(value & 1);
    }
}
