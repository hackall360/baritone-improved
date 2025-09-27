package baritone.cache.seed;

/**
 * Layout constants for the 32x32 chunk region blobs.
 */
public final class RegionLayout {

    public static final int CHUNK_SIZE = 16;
    public static final int REGION_SIZE = 32;
    public static final int CHUNK_COUNT = REGION_SIZE * REGION_SIZE;

    public static int index(int baseChunkX, int baseChunkZ, int chunkX, int chunkZ) {
        int dx = chunkX - baseChunkX;
        int dz = chunkZ - baseChunkZ;
        if (dx < 0 || dz < 0 || dx >= REGION_SIZE || dz >= REGION_SIZE) {
            throw new IllegalArgumentException("Chunk outside region");
        }
        return dz * REGION_SIZE + dx;
    }

    private RegionLayout() {
    }
}
