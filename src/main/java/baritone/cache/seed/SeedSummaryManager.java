package baritone.cache.seed;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages predicted chunk summaries keyed by region coordinate and exposes aggregated metrics for planning.
 */
public final class SeedSummaryManager {

    private static final int SEA_LEVEL = 63;

    private final BlockCompressor compressor;
    private final Map<Long, RegionBlobReader> regions = new ConcurrentHashMap<>();
    private final Map<Long, SurfaceMetrics> overrides = new ConcurrentHashMap<>();
    private final Map<Long, SurfaceMetrics> metricsCache = new ConcurrentHashMap<>();

    public SeedSummaryManager(BlockCompressor compressor) {
        this.compressor = compressor;
    }

    public void putRegionBlob(int baseChunkX, int baseChunkZ, byte[] blob) {
        int regionX = Math.floorDiv(baseChunkX, RegionLayout.REGION_SIZE);
        int regionZ = Math.floorDiv(baseChunkZ, RegionLayout.REGION_SIZE);
        long key = regionKey(regionX, regionZ);
        regions.put(key, new RegionBlobReader(blob, compressor));
        invalidateRegion(regionX, regionZ);
    }

    public void clearAll() {
        regions.clear();
        overrides.clear();
        metricsCache.clear();
    }

    public ChunkSummaryHeader readHeader(int chunkX, int chunkZ) {
        SurfaceMetrics override = overrides.get(chunkKey(chunkX, chunkZ));
        if (override != null) {
            // We don't keep full headers for overrides, so fall back to empty header.
            return ChunkSummaryHeader.empty();
        }
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null ? region.readHeader(chunkX, chunkZ) : ChunkSummaryHeader.empty();
    }

    public boolean hasCave(int chunkX, int chunkZ) {
        SurfaceMetrics override = overrides.get(chunkKey(chunkX, chunkZ));
        if (override != null) {
            return override.hasCave();
        }
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.hasCave(chunkX, chunkZ);
    }

    public boolean isPassable(int chunkX, int chunkZ, int localX, int localZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.isPassable(chunkX, chunkZ, localX, localZ);
    }

    public int topY(int chunkX, int chunkZ, int localX, int localZ) {
        SurfaceMetrics override = overrides.get(chunkKey(chunkX, chunkZ));
        if (override != null && override.passableRatio() > 0.0) {
            return (int) Math.round(override.averageY());
        }
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null ? region.topY(chunkX, chunkZ, localX, localZ) : 0;
    }

    public boolean hasPassableSurface(int chunkX, int chunkZ) {
        SurfaceMetrics override = overrides.get(chunkKey(chunkX, chunkZ));
        if (override != null) {
            return override.passableRatio() > 0.05;
        }
        RegionBlobReader region = region(chunkX, chunkZ);
        return region != null && region.hasPassableSurface(chunkX, chunkZ);
    }

    public SurfaceMetrics metrics(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        SurfaceMetrics override = overrides.get(key);
        if (override != null) {
            return override;
        }
        return metricsCache.computeIfAbsent(key, k -> computeMetrics(chunkX, chunkZ));
    }

    public void overrideSurfaceMetrics(int chunkX, int chunkZ, SurfaceMetrics metrics) {
        long key = chunkKey(chunkX, chunkZ);
        if (metrics == null) {
            overrides.remove(key);
            metricsCache.remove(key);
        } else {
            overrides.put(key, metrics);
            metricsCache.put(key, metrics);
        }
    }

    private SurfaceMetrics computeMetrics(int chunkX, int chunkZ) {
        RegionBlobReader region = region(chunkX, chunkZ);
        if (region == null) {
            return SurfaceMetrics.EMPTY;
        }
        BitSet passability = region.passabilityBits(chunkX, chunkZ);
        PackedHeightField heights = region.heightField(chunkX, chunkZ);
        boolean hasCave = region.hasCave(chunkX, chunkZ);
        if (passability == null && heights == null) {
            return SurfaceMetrics.EMPTY;
        }
        int totalColumns = 16 * 16;
        double passableRatio = passability != null ? passability.cardinality() / (double) totalColumns : 0.0;
        if (heights == null) {
            double waterGuess = passableRatio > 0.8 ? 0.5 : 0.0;
            double lavaGuess = 0.0;
            return new SurfaceMetrics(passableRatio, SEA_LEVEL, SEA_LEVEL, SEA_LEVEL, hasCave, false, false, passableRatio < 0.6,
                    waterGuess, lavaGuess, 0.0);
        }
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        double sum = 0.0;
        int lowCount = 0;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int y = heights.topY(localX, localZ);
                min = Math.min(min, y);
                max = Math.max(max, y);
                sum += y;
            }
        }
        double average = sum / totalColumns;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int y = heights.topY(localX, localZ);
                if (y < average - 3) {
                    lowCount++;
                }
            }
        }
        boolean likelyHole = lowCount > totalColumns * 0.1 || max - min >= 6;
        boolean likelyWater = passableRatio > 0.85 && Math.abs(average - SEA_LEVEL) <= 1 && max - min <= 2;
        boolean likelyLava = average < 20 && passableRatio > 0.4;
        double waterEstimate = likelyWater ? Math.max(0.4, 1.0 - (1.0 - passableRatio) * 0.5) : 0.0;
        double lavaEstimate = likelyLava ? Math.min(0.5, (20 - Math.min(average, 20)) / 40.0 + 0.2) : 0.0;
        return new SurfaceMetrics(passableRatio, min, max, average, hasCave, likelyWater, likelyLava, likelyHole, waterEstimate, lavaEstimate, 0.0);
    }

    private RegionBlobReader region(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, RegionLayout.REGION_SIZE);
        int regionZ = Math.floorDiv(chunkZ, RegionLayout.REGION_SIZE);
        return regions.get(regionKey(regionX, regionZ));
    }

    private void invalidateRegion(int regionX, int regionZ) {
        int baseChunkX = regionX * RegionLayout.REGION_SIZE;
        int baseChunkZ = regionZ * RegionLayout.REGION_SIZE;
        for (int dz = 0; dz < RegionLayout.REGION_SIZE; dz++) {
            for (int dx = 0; dx < RegionLayout.REGION_SIZE; dx++) {
                metricsCache.remove(chunkKey(baseChunkX + dx, baseChunkZ + dz));
            }
        }
    }

    private static long regionKey(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
