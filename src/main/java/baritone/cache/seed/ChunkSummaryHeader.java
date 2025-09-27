package baritone.cache.seed;

import java.util.EnumSet;
import java.util.Set;

/**
 * Small bitfield describing which summaries are present for a chunk.
 */
public final class ChunkSummaryHeader {

    private static final byte VERSION_MASK = (byte) 0xC0;
    private static final byte FIELD_MASK = 0x3F;

    private final byte mask;

    private ChunkSummaryHeader(byte mask) {
        this.mask = mask;
    }

    public static ChunkSummaryHeader of(Set<ChunkField> fields) {
        byte mask = 0;
        for (ChunkField field : fields) {
            mask |= field.bit();
        }
        return new ChunkSummaryHeader(mask);
    }

    public static ChunkSummaryHeader empty() {
        return new ChunkSummaryHeader((byte) 0);
    }

    public static ChunkSummaryHeader fromByte(byte value) {
        if ((value & VERSION_MASK) != 0) {
            throw new IllegalArgumentException("Unknown chunk summary header version " + value);
        }
        return new ChunkSummaryHeader((byte) (value & FIELD_MASK));
    }

    public byte toByte() {
        return mask;
    }

    public boolean hasField(ChunkField field) {
        return (mask & field.bit()) != 0;
    }

    public Set<ChunkField> fields() {
        EnumSet<ChunkField> set = EnumSet.noneOf(ChunkField.class);
        for (ChunkField field : ChunkField.values()) {
            if (hasField(field)) {
                set.add(field);
            }
        }
        return set;
    }
}
