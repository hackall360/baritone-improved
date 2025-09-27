package baritone.cache.seed;

/**
 * Single byte micro-index stored in the region header. It lets the planner skip chunks that
 * obviously do not contain the data it is searching for without touching the heavier blobs.
 */
public enum MicroIndex {
    HAS_CAVE(1 << 0),
    HAS_STRUCTURE(1 << 1),
    HAS_HIGH_ORE(1 << 2),
    HEIGHT_TRUNCATED(1 << 3),
    HAS_SURFACE_PORTAL(1 << 4),
    HAS_PASSABLE_SURFACE(1 << 5);

    private final int bit;

    MicroIndex(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}
