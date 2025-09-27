package baritone.cache.seed;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages predicted chunk summaries keyed by region coordinate.
 */
public final class SeedSummaryManager {

    private final BlockCompressor compressor;
    private final Map<Long, RegionBlobReader> regions = new ConcurrentHashMap<>();

    public SeedSummaryManager(BlockCompressor compressor) {
        this.compressor = compressor;
    }

    public void putRegionBlob(int baseChunkX, int baseChunkZ, byte[] blob) {
        int regionX = Math.floorDiv(baseChunkX, RegionLayout.REGION_SIZE);
        int regionZ = Math.floorDiv(baseChunkZ, RegionLayout.REGION_SIZE);
        regions.put(regionKey(regionX, regionZ), new RegionBlobReader(blob, compressor));
    }

    public ChunkSummaryHeader readHeader(int chunkX, int chunkZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null ? region.readHeader(chunkX, chunkZ) : ChunkSummaryHeader.empty();
    }

    public boolean hasCave(int chunkX, int chunkZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.hasCave(chunkX, chunkZ);
    }

    public boolean isPassable(int chunkX, int chunkZ, int localX, int localZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.isPassable(chunkX, chunkZ, localX, localZ);
    }

    public int topY(int chunkX, int chunkZ, int localX, int localZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null ? region.topY(chunkX, chunkZ, localX, localZ) : 0;
    }

    public boolean hasPassableSurface(int chunkX, int chunkZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.hasPassableSurface(chunkX, chunkZ);
    }

    private RegionBlobReader region(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, RegionLayout.REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, RegionLayout.REGION_SIZE);
        return regions.get(regionKey(regionX, regionZ));
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
