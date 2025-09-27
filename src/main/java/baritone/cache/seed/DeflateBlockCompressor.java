package baritone.cache.seed;

import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * CPU friendly fallback compressor that uses the JDK's {@link Deflater}. It keeps the
 * block compression API pluggable so that faster LZ4/Zstd implementations can be
 * swapped in without touching the planner code.
 */
public final class DeflateBlockCompressor implements BlockCompressor {

    private final int level;

    public DeflateBlockCompressor() {
        this(Deflater.BEST_SPEED);
    }

    public DeflateBlockCompressor(int level) {
        this.level = level;
    }

    @Override
    public byte[] compress(byte[] data) {
        if (data.length == 0) {
            return data;
        }
        Deflater deflater = new Deflater(level);
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[data.length + (data.length >> 1) + 16];
        int written = deflater.deflate(buffer);
        deflater.end();
        byte[] result = new byte[written];
        System.arraycopy(buffer, 0, result, 0, written);
        return result;
    }

    @Override
    public byte[] decompress(byte[] data, int expectedLength) {
        if (data.length == 0) {
            return data;
        }
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        byte[] result = new byte[expectedLength];
        try {
            int written = inflater.inflate(result);
            if (written != expectedLength) {
                throw new IllegalStateException("Unexpected decompressed size " + written + " != " + expectedLength);
            }
            return result;
        } catch (DataFormatException e) {
            throw new IllegalStateException("Failed to inflate chunk field", e);
        } finally {
            inflater.end();
        }
    }
}
