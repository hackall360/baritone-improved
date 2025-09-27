package baritone.cache.seed;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * High level representation of a chunk summary prior to packing it into a compact byte[] blob.
 */
public final class ChunkSummaryRecord {

    private final ChunkSummaryHeader header;
    private final byte[] passability;
    private final PackedHeightField heights;
    private final byte[] caveBands;
    private final byte[] oreBands;
    private final byte[] structures;
    private final byte[] extras;
    private final byte microIndex;

    private ChunkSummaryRecord(Builder builder) {
        this.passability = builder.passability;
        this.heights = builder.heights;
        this.caveBands = builder.caveBands;
        this.oreBands = builder.oreBands;
        this.structures = builder.structures;
        this.extras = builder.extras;
        this.microIndex = builder.computeMicroIndex();
        Set<ChunkField> fields = EnumSet.noneOf(ChunkField.class);
        if (passability != null) {
            fields.add(ChunkField.PASSABILITY);
        }
        if (heights != null) {
            fields.add(ChunkField.HEIGHTS);
        }
        if (caveBands != null) {
            fields.add(ChunkField.CAVES);
        }
        if (oreBands != null) {
            fields.add(ChunkField.ORES);
        }
        if (structures != null) {
            fields.add(ChunkField.STRUCTURES);
        }
        if (extras != null) {
            fields.add(ChunkField.EXTRAS);
        }
        this.header = ChunkSummaryHeader.of(fields);
    }

    public ChunkSummaryHeader header() {
        return header;
    }

    public byte[] passability() {
        return passability;
    }

    public PackedHeightField heights() {
        return heights;
    }

    public byte[] caveBands() {
        return caveBands;
    }

    public byte[] oreBands() {
        return oreBands;
    }

    public byte[] structures() {
        return structures;
    }

    public byte[] extras() {
        return extras;
    }

    public byte microIndex() {
        return microIndex;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private byte[] passability;
        private PackedHeightField heights;
        private byte[] caveBands;
        private byte[] oreBands;
        private byte[] structures;
        private byte[] extras;
        private boolean highOre;
        private boolean hasSurfacePortal;

        private Builder() {
        }

        public Builder passability(BitSet bitset) {
            this.passability = PassabilityField.pack(bitset);
            return this;
        }

        public Builder passabilityBytes(byte[] packed) {
            this.passability = Objects.requireNonNull(packed);
            return this;
        }

        public Builder heights(PackedHeightField heights) {
            this.heights = Objects.requireNonNull(heights);
            return this;
        }

        public Builder caveBands(byte[] caveBands) {
            this.caveBands = Objects.requireNonNull(caveBands);
            return this;
        }

        public Builder oreBands(byte[] oreBands, boolean hasHighOre) {
            this.oreBands = Objects.requireNonNull(oreBands);
            this.highOre = hasHighOre;
            return this;
        }

        public Builder structures(byte[] structures) {
            this.structures = Objects.requireNonNull(structures);
            return this;
        }

        public Builder extras(byte[] extras) {
            this.extras = Objects.requireNonNull(extras);
            return this;
        }

        public Builder surfacePortal(boolean value) {
            this.hasSurfacePortal = value;
            return this;
        }

        public ChunkSummaryRecord build() {
            return new ChunkSummaryRecord(this);
        }

        private byte computeMicroIndex() {
            byte value = 0;
            if (caveBands != null && VerticalBandField.hasAnyEncoded(caveBands)) {
                value |= MicroIndex.HAS_CAVE.bit();
            }
            if (structures != null && structures.length > 0) {
                value |= MicroIndex.HAS_STRUCTURE.bit();
            }
            if (highOre) {
                value |= MicroIndex.HAS_HIGH_ORE.bit();
            }
            if (heights != null && heights.isTruncated()) {
                value |= MicroIndex.HEIGHT_TRUNCATED.bit();
            }
            if (hasSurfacePortal) {
                value |= MicroIndex.HAS_SURFACE_PORTAL.bit();
            }
            if (passability != null && PassabilityField.hasAnyPassable(passability)) {
                value |= MicroIndex.HAS_PASSABLE_SURFACE.bit();
            }
            return value;
        }
    }
}
