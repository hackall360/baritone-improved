package baritone.pathing.seed.pregen;

public record SectionBlob(int chunkX, int sectionY, int chunkZ, byte[] rleData, int runCount, int faceMask, int[] parents) {
}
