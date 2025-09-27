package baritone.cache.seed;

import baritone.utils.io.VarIntUtils;

import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lightweight parsed view over a serialized chunk record.
 */
public final class ChunkSummaryRecordView {

    private final ByteBuffer payload;
    private final ChunkSummaryHeader header;
    private final Map<ChunkField, FieldEntry> fields;

    ChunkSummaryRecordView(ByteBuffer payload) {
        this.payload = payload.slice();
        ByteBuffer cursor = this.payload.duplicate();
        byte headerByte = cursor.get();
        this.header = ChunkSummaryHeader.fromByte(headerByte);
        this.fields = new EnumMap<>(ChunkField.class);
        for (ChunkField field : ChunkField.values()) {
            if (header.hasField(field)) {
                int compressedLength = VarIntUtils.readUnsigned(cursor);
                int rawLength = VarIntUtils.readUnsigned(cursor);
                fields.put(field, new FieldEntry(compressedLength, rawLength));
            }
        }
        int headerSize = cursor.position();
        int offset = 0;
        for (ChunkField field : ChunkField.values()) {
            FieldEntry entry = fields.get(field);
            if (entry != null) {
                fields.put(field, entry.withOffset(headerSize + offset));
                offset += entry.compressedLength;
            }
        }
    }

    public ChunkSummaryHeader header() {
        return header;
    }

    public boolean hasField(ChunkField field) {
        return header.hasField(field);
    }

    public byte[] readField(ChunkField field, BlockCompressor compressor) {
        FieldEntry entry = fields.get(field);
        if (entry == null) {
            return null;
        }
        ByteBuffer dup = payload.duplicate();
        dup.position(entry.offset);
        byte[] compressed = new byte[entry.compressedLength];
        dup.get(compressed);
        return compressor.decompress(compressed, entry.rawLength);
    }

    private record FieldEntry(int compressedLength, int rawLength, int offset) {
        FieldEntry(int compressedLength, int rawLength) {
            this(compressedLength, rawLength, -1);
        }

        FieldEntry withOffset(int offset) {
            return new FieldEntry(compressedLength, rawLength, offset);
        }
    }
}
