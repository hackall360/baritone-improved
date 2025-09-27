package baritone.cache.seed;

/**
 * Simple pluggable block compressor used to pack individual chunk fields.
 * Implementations can wrap native libraries (LZ4, Zstd, etc) while the planner
 * only depends on this small API.
 */
public interface BlockCompressor {

    byte[] compress(byte[] data);

    byte[] decompress(byte[] data, int expectedLength);

    static BlockCompressor noCompression() {
        return new BlockCompressor() {
            @Override
            public byte[] compress(byte[] data) {
                return data;
            }

            @Override
            public byte[] decompress(byte[] data, int expectedLength) {
                return data;
            }
        };
    }
}
