package baritone.pathing.seed.pregen;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SectionStore {

    private final Path root;

    public SectionStore(Path root) {
        this.root = root;
    }

    public void write(SectionBlob blob) {
        try {
            Path dimensionDir = root;
            Files.createDirectories(dimensionDir);
            Path directory = dimensionDir.resolve((blob.chunkX() >> 5) + "_" + (blob.chunkZ() >> 5));
            Files.createDirectories(directory);
            Path file = directory.resolve(blob.chunkX() + "_" + blob.sectionY() + "_" + blob.chunkZ() + ".psec");
            try (BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE))) {
                writeVarInt(output, blob.chunkX());
                writeVarInt(output, blob.sectionY());
                writeVarInt(output, blob.chunkZ());
                writeVarInt(output, blob.runCount());
                writeVarInt(output, blob.faceMask());
                byte[] rle = blob.rleData();
                writeVarInt(output, rle.length);
                output.write(rle);
                int[] parents = blob.parents();
                writeVarInt(output, parents.length);
                ByteBuffer buffer = ByteBuffer.allocate(parents.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                for (int value : parents) {
                    buffer.putInt(value);
                }
                output.write(buffer.array());
                output.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void writeVarInt(OutputStream output, int value) throws IOException {
        int current = value;
        while ((current & 0xFFFFFF80) != 0) {
            output.write((current & 0x7F) | 0x80);
            current >>>= 7;
        }
        output.write(current & 0x7F);
    }
}
