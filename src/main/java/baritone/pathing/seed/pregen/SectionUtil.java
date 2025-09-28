package baritone.pathing.seed.pregen;

public final class SectionUtil {

    private SectionUtil() {
    }

    public static int minSectionY(GeneratorContext context) {
        return context.noiseSettings().value().noiseSettings().minY() >> 4;
    }

    public static int maxSectionY(GeneratorContext context) {
        int minY = context.noiseSettings().value().noiseSettings().minY();
        int height = context.noiseSettings().value().noiseSettings().height();
        return ((minY + height) >> 4) - 1;
    }

    public static long key(int chunkX, int sectionY, int chunkZ) {
        long kx = (long) chunkX & 0x3FFFFFL;
        long ky = (long) sectionY & 0x3FFL;
        long kz = (long) chunkZ & 0x3FFFFFL;
        return (kx << 42) | (ky << 32) | kz;
    }
}
