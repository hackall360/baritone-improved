package baritone.cache.seed;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;

import baritone.utils.io.VarIntUtils;

/**
 * Packs and unpacks {@link ChunkSummaryRecord} instances to the compact on-disk representation.
 */
public final class ChunkSummaryPacker {

    private ChunkSummaryPacker() {
    }

    public static EncodedChunk encode(ChunkSummaryRecord record, BlockCompressor compressor) {
        Map<ChunkField, FieldBlock> blocks = new EnumMap<>(ChunkField.class);
        for (ChunkField field : record.header().fields()) {
            byte[] raw;
            switch (field) {
                case PASSABILITY:
                    raw = record.passability();
                    break;
                case HEIGHTS:
                    raw = record.heights().toByteArray();
                    break;
                case CAVES:
                    raw = record.caveBands();
                    break;
                case ORES:
                    raw = record.oreBands();
                    break;
                case STRUCTURES:
                    raw = record.structures();
                    break;
                case EXTRAS:
                    raw = record.extras();
                    break;
                default:
                    throw new IllegalStateException("Unknown field " + field);
            }
            byte[] compressed = compressor.compress(raw);
            blocks.put(field, new FieldBlock(compressed, raw.length));
        }

        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        headerOut.write(record.header().toByte());
        for (ChunkField field : ChunkField.values()) {
            if (record.header().hasField(field)) {
                FieldBlock block = blocks.get(field);
                VarIntUtils.writeUnsigned(headerOut, block.compressed.length);
                VarIntUtils.writeUnsigned(headerOut, block.rawLength);
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(headerOut.toByteArray());
        for (ChunkField field : ChunkField.values()) {
            if (record.header().hasField(field)) {
                out.writeBytes(blocks.get(field).compressed);
            }
        }
        return new EncodedChunk(out.toByteArray(), record.microIndex());
    }

    public static ChunkSummaryRecordView view(ByteBuffer buffer) {
        return new ChunkSummaryRecordView(buffer.slice());
    }

    public record EncodedChunk(byte[] payload, byte microIndex) {
    }

    private record FieldBlock(byte[] compressed, int rawLength) {
    }
}
