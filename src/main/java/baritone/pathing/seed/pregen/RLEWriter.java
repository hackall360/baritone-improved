package baritone.pathing.seed.pregen;

import java.io.ByteArrayOutputStream;

public final class RLEWriter {

    private final ByteArrayOutputStream output;
    private int last = -1;
    private int runLength = 0;
    private int runCount = 0;

    public RLEWriter(int reserve) {
        this.output = new ByteArrayOutputStream(Math.max(0, reserve));
    }

    public void put(int value) {
        if (value == last) {
            runLength++;
            return;
        }
        flush();
        last = value;
        runLength = 1;
    }

    public byte[] finish() {
        flush();
        return output.toByteArray();
    }

    public int runCount() {
        return runCount;
    }

    private void flush() {
        if (runLength == 0) {
            return;
        }
        output.write((byte) last);
        writeVarInt(runLength);
        runCount++;
        runLength = 0;
        last = -1;
    }

    private void writeVarInt(int value) {
        int current = value;
        while ((current & 0xFFFFFF80) != 0) {
            output.write((current & 0x7F) | 0x80);
            current >>>= 7;
        }
        output.write(current & 0x7F);
    }
}
