package baritone.pathing.seed.pregen;

import net.minecraft.world.level.levelgen.NoiseRouter;

public final class VeinMarker {

    private VeinMarker() {
    }

    public static void fastFlagVeins(NoiseRouter router, int chunkX, int sectionY, int chunkZ, byte[] categories) {
        TrilinearSampler sampler = new TrilinearSampler(
                null,
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap(),
                router.lavaNoise(),
                chunkX,
                sectionY,
                chunkZ
        );

        int index = 0;
        for (int y = 0; y < 16; y++) {
            int worldY = (sectionY << 4) + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++, index++) {
                    byte current = categories[index];
                    if (current != SectionGenerator.SOLID) {
                        continue;
                    }
                    double toggle = sampler.finalDensityAt(x, y, z);
                    double ridged = sampler.aquiferAt(x, y, z).isWater() ? 1.0D : -1.0D;
                    double weight = toggle * 0.8D + ridged * 0.2D;
                    if (Math.abs(weight) < 0.55D) {
                        continue;
                    }
                    boolean deepslate = worldY < 0;
                    if (weight > 0.0D) {
                        categories[index] = deepslate ? SectionGenerator.O_DEEPSLATE_IRON : SectionGenerator.O_IRON;
                    } else {
                        if (worldY > 48) {
                            categories[index] = deepslate ? SectionGenerator.O_DEEPSLATE_COPPER : SectionGenerator.O_COPPER;
                        } else {
                            categories[index] = deepslate ? SectionGenerator.O_DEEPSLATE_GOLD : SectionGenerator.O_GOLD;
                        }
                    }
                }
            }
        }
    }
}
