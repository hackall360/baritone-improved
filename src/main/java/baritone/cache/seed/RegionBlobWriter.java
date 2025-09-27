package baritone.cache.seed;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * Serialises region wide chunk summaries into a compact blob.
 */
public final class RegionBlobWriter {

    private static final int MAGIC = 0x53435247; // "SCRG"
    private static final short VERSION = 1;

    private RegionBlobWriter() {
    }

    public static byte[] write(int baseChunkX, int baseChunkZ, Map<Long, ChunkSummaryRecord> chunks, BlockCompressor compressor) {
        try {
            ByteArrayOutputStream chunkOut = new ByteArrayOutputStream();
            byte[] microIndex = new byte[RegionLayout.CHUNK_COUNT];
            int[] offsets = new int[RegionLayout.CHUNK_COUNT];
            int[] lengths = new int[RegionLayout.CHUNK_COUNT];
            Arrays.fill(offsets, -1);
            Arrays.fill(lengths, 0);

            for (Map.Entry<Long, ChunkSummaryRecord> entry : chunks.entrySet()) {
                ChunkSummaryRecord record = entry.getValue();
                ChunkSummaryPacker.EncodedChunk encoded = ChunkSummaryPacker.encode(record, compressor);
                int chunkX = (int) (entry.getKey() >> 32);
                int chunkZ = entry.getKey().intValue();
                int index = RegionLayout.index(baseChunkX, baseChunkZ, chunkX, chunkZ);
                offsets[index] = chunkOut.size();
                lengths[index] = encoded.payload().length;
                chunkOut.writeBytes(encoded.payload());
                microIndex[index] = encoded.microIndex();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream data = new DataOutputStream(out);
            data.writeInt(MAGIC);
            data.writeShort(VERSION);
            data.writeInt(baseChunkX);
            data.writeInt(baseChunkZ);
            data.writeShort(RegionLayout.CHUNK_SIZE);
            data.writeShort(RegionLayout.REGION_SIZE);
            data.writeInt(RegionLayout.CHUNK_COUNT);
            data.writeInt(microIndex.length);
            data.write(microIndex);
            for (int i = 0; i < RegionLayout.CHUNK_COUNT; i++) {
                data.writeInt(offsets[i]);
                data.writeInt(lengths[i]);
            }
            data.write(chunkOut.toByteArray());
            data.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode region blob", e);
        }
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
