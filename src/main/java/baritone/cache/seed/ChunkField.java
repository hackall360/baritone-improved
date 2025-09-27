package baritone.cache.seed;

/**
 * Logical fields stored inside a chunk summary blob. Each field is compressed as an
 * independent block so that readers can lazily materialize only the data that a query
 * needs.
 */
public enum ChunkField {
    PASSABILITY(1 << 0),
    HEIGHTS(1 << 1),
    CAVES(1 << 2),
    ORES(1 << 3),
    STRUCTURES(1 << 4),
    EXTRAS(1 << 5);

    private final int bit;

    ChunkField(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }
}
