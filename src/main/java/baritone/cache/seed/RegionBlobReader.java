package baritone.cache.seed;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Objects;

/**
 * Provides random access over a region blob without eager decompression.
 */
public final class RegionBlobReader {

    private static final int MAGIC = 0x53435247;

    private final int baseChunkX;
    private final int baseChunkZ;
    private final byte[] microIndex;
    private final int[] offsets;
    private final int[] lengths;
    private final ByteBuffer data;
    private final BlockCompressor compressor;
    public RegionBlobReader(byte[] blob, BlockCompressor compressor) {
        this(ByteBuffer.wrap(blob), compressor);
    }

    public RegionBlobReader(ByteBuffer buffer, BlockCompressor compressor) {
        Objects.requireNonNull(compressor, "compressor");
        buffer = buffer.slice();
        int magic = buffer.getInt();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Not a seed chunk region");
        }
        short version = buffer.getShort();
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported region version " + version);
        }
        this.baseChunkX = buffer.getInt();
        this.baseChunkZ = buffer.getInt();
        short chunkSize = buffer.getShort();
        short regionSize = buffer.getShort();
        if (chunkSize != RegionLayout.CHUNK_SIZE || regionSize != RegionLayout.REGION_SIZE) {
            throw new IllegalArgumentException("Unexpected region layout");
        }
        int chunkCount = buffer.getInt();
        if (chunkCount != RegionLayout.CHUNK_COUNT) {
            throw new IllegalArgumentException("Unexpected chunk count");
        }
        int microLength = buffer.getInt();
        this.microIndex = new byte[microLength];
        buffer.get(this.microIndex);
        this.offsets = new int[chunkCount];
        this.lengths = new int[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            offsets[i] = buffer.getInt();
            lengths[i] = buffer.getInt();
        }
        this.data = buffer.slice();
        this.compressor = compressor;
    }

    public ChunkSummaryHeader readHeader(int chunkX, int chunkZ) {
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        return view != null ? view.header() : ChunkSummaryHeader.empty();
    }

    public boolean hasCave(int chunkX, int chunkZ) {
        int index = chunkIndex(chunkX, chunkZ);
        if (index < 0) {
            return false;
        }
        return (microIndex[index] & MicroIndex.HAS_CAVE.bit()) != 0;
    }

    public boolean isPassable(int chunkX, int chunkZ, int localX, int localZ) {
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        if (view == null || !view.hasField(ChunkField.PASSABILITY)) {
            return false;
        }
        byte[] raw = view.readField(ChunkField.PASSABILITY, compressor);
        BitSetCache cache = BitSetCache.fromPacked(raw);
        return cache.isPassable(localX, localZ);
    }

    public BitSet passabilityBits(int chunkX, int chunkZ) {
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        if (view == null || !view.hasField(ChunkField.PASSABILITY)) {
            return null;
        }
        byte[] raw = view.readField(ChunkField.PASSABILITY, compressor);
        return PassabilityField.unpack(raw);
    }

    public boolean hasPassableSurface(int chunkX, int chunkZ) {
        int index = chunkIndex(chunkX, chunkZ);
        if (index < 0) {
            return false;
        }
        if ((microIndex[index] & MicroIndex.HAS_PASSABLE_SURFACE.bit()) != 0) {
            return true;
        }
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        if (view == null || !view.hasField(ChunkField.PASSABILITY)) {
            return false;
        }
        byte[] raw = view.readField(ChunkField.PASSABILITY, compressor);
        return PassabilityField.hasAnyPassable(raw);
    }

    public int topY(int chunkX, int chunkZ, int localX, int localZ) {
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        if (view == null || !view.hasField(ChunkField.HEIGHTS)) {
            return 0;
        }
        byte[] raw = view.readField(ChunkField.HEIGHTS, compressor);
        PackedHeightField field = PackedHeightField.decode(raw);
        return field.topY(localX, localZ);
    }

    public PackedHeightField heightField(int chunkX, int chunkZ) {
        ChunkSummaryRecordView view = view(chunkX, chunkZ);
        if (view == null || !view.hasField(ChunkField.HEIGHTS)) {
            return null;
        }
        byte[] raw = view.readField(ChunkField.HEIGHTS, compressor);
        return PackedHeightField.decode(raw);
    }

    private ChunkSummaryRecordView view(int chunkX, int chunkZ) {
        int index = chunkIndex(chunkX, chunkZ);
        if (index < 0) {
            return null;
        }
        int offset = offsets[index];
        int length = lengths[index];
        if (offset < 0 || length == 0) {
            return null;
        }
        ByteBuffer slice = data.duplicate();
        slice.position(offset);
        slice.limit(offset + length);
        return new ChunkSummaryRecordView(slice.slice());
    }

    private int chunkIndex(int chunkX, int chunkZ) {
        try {
            return RegionLayout.index(baseChunkX, baseChunkZ, chunkX, chunkZ);
        } catch (IllegalArgumentException ignored) {
            return -1;
        }
    }

    private static final class BitSetCache {
        private final java.util.BitSet bitset;

        private BitSetCache(java.util.BitSet bitset) {
            this.bitset = bitset;
        }

        static BitSetCache fromPacked(byte[] data) {
            return new BitSetCache(PassabilityField.unpack(data));
        }

        boolean isPassable(int localX, int localZ) {
            int index = localZ * 16 + localX;
            return bitset.get(index);
        }
    }
}
