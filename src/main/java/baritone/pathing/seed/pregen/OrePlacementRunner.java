package baritone.pathing.seed.pregen;

import net.minecraft.world.level.levelgen.NoiseRouter;

import java.util.Random;

public final class OrePlacementRunner {

    private OrePlacementRunner() {
    }

    public static void placeExactOres(GeneratorContext ctx, int chunkX, int sectionY, int chunkZ, byte[] categories) {
        NoiseRouter router = ctx.randomState().router();
        TrilinearSampler toggleSampler = new TrilinearSampler(
                ctx.randomState(),
                router.veinToggle(),
                router.veinRidged(),
                router.veinGap(),
                router.lavaNoise(),
                chunkX,
                sectionY,
                chunkZ
        );
        TrilinearSampler densitySampler = new TrilinearSampler(
                ctx.randomState(),
                router.finalDensity(),
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.lavaNoise(),
                chunkX,
                sectionY,
                chunkZ
        );

        long baseSeed = ctx.seed() ^ ((long) chunkX * 341873128712L) ^ ((long) chunkZ * 132897987541L);
        Random coarseRandom = new Random(baseSeed);

        int index = 0;
        for (int y = 0; y < 16; y++) {
            int worldY = (sectionY << 4) + y;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++, index++) {
                    if (categories[index] != SectionGenerator.SOLID) {
                        continue;
                    }
                    double density = densitySampler.finalDensityAt(x, y, z);
                    if (density <= 0.0D) {
                        continue;
                    }
                    double toggle = toggleSampler.finalDensityAt(x, y, z);
                    double ridged = toggleSampler.aquiferAt(x, y, z).isWater() ? 1.0D : -1.0D;
                    double gap = Math.abs(toggleSampler.aquiferAt(x, y, z).isLava() ? 1.0D : 0.0D);
                    if (gap > 0.6D) {
                        continue;
                    }
                    int worldX = (chunkX << 4) + x;
                    int worldZ = (chunkZ << 4) + z;
                    double noise = hashedNoise(baseSeed, worldX, worldY, worldZ);
                    byte category = selectOreCategory(worldY, toggle, ridged, noise, coarseRandom);
                    if (category != SectionGenerator.SOLID) {
                        categories[index] = category;
                    }
                }
            }
        }
    }

    private static double hashedNoise(long seed, int x, int y, int z) {
        long value = seed;
        value ^= x * 0x632BE59BD9B4E019L;
        value ^= y * 0x9E3779B97F4A7C15L;
        value ^= z * 0xC2B2AE3D27D4EB4FL;
        value ^= Long.rotateLeft(value, 13);
        value *= 0x9E3779B97F4A7C15L;
        return (value >>> 11) * (1.0D / (1L << 53));
    }

    private static byte selectOreCategory(int worldY, double toggle, double ridged, double noise, Random random) {
        boolean deepslate = worldY < 0;
        int absY = Math.abs(worldY);
        double bias = toggle * 0.6D + ridged * 0.2D + (noise - 0.5D) * 0.4D;
        if (worldY > 128 && random.nextDouble() < 0.35D) {
            return deepslate ? SectionGenerator.O_DEEPSLATE_EMERALD : SectionGenerator.O_EMERALD;
        }
        if (worldY > 80) {
            if (bias > 0.25D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_COAL : SectionGenerator.O_COAL;
            }
            if (bias < -0.25D && random.nextDouble() < 0.4D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_IRON : SectionGenerator.O_IRON;
            }
            return SectionGenerator.SOLID;
        }
        if (worldY > 40) {
            if (bias > 0.15D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_COPPER : SectionGenerator.O_COPPER;
            }
            if (bias < -0.2D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_IRON : SectionGenerator.O_IRON;
            }
            if (noise > 0.75D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_COAL : SectionGenerator.O_COAL;
            }
            return SectionGenerator.SOLID;
        }
        if (worldY > 0) {
            if (bias > 0.2D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_IRON : SectionGenerator.O_IRON;
            }
            if (bias < -0.15D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_GOLD : SectionGenerator.O_GOLD;
            }
            if (noise > 0.8D) {
                return deepslate ? SectionGenerator.O_DEEPSLATE_REDSTONE : SectionGenerator.O_REDSTONE;
            }
            return SectionGenerator.SOLID;
        }
        if (worldY > -32) {
            if (bias > 0.25D) {
                return SectionGenerator.O_DEEPSLATE_DIAMOND;
            }
            if (bias < -0.25D) {
                return SectionGenerator.O_DEEPSLATE_REDSTONE;
            }
            if (noise > 0.7D) {
                return SectionGenerator.O_DEEPSLATE_GOLD;
            }
            if (noise < 0.2D) {
                return SectionGenerator.O_DEEPSLATE_LAPIS;
            }
            return SectionGenerator.SOLID;
        }
        if (absY > 96 && random.nextDouble() < 0.3D) {
            return SectionGenerator.O_DEEPSLATE_EMERALD;
        }
        if (bias > 0.15D) {
            return SectionGenerator.O_DEEPSLATE_DIAMOND;
        }
        if (bias < -0.15D) {
            return SectionGenerator.O_DEEPSLATE_LAPIS;
        }
        return noise > 0.6D ? SectionGenerator.O_DEEPSLATE_REDSTONE : SectionGenerator.SOLID;
    }
}
