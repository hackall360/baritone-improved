package baritone.pathing.seed.pregen;

import baritone.api.pathing.seed.SeedOreMode;
import net.minecraft.world.level.levelgen.NoiseRouter;

public final class SectionGenerator {

    public static final byte SOLID = 0;
    public static final byte AIR = 1;
    public static final byte WATER = 2;
    public static final byte LAVA = 3;
    public static final byte O_COAL = 4;
    public static final byte O_IRON = 5;
    public static final byte O_COPPER = 6;
    public static final byte O_GOLD = 7;
    public static final byte O_LAPIS = 8;
    public static final byte O_DIAMOND = 9;
    public static final byte O_REDSTONE = 10;
    public static final byte O_EMERALD = 11;
    public static final byte O_DEEPSLATE_COAL = 12;
    public static final byte O_DEEPSLATE_IRON = 13;
    public static final byte O_DEEPSLATE_COPPER = 14;
    public static final byte O_DEEPSLATE_GOLD = 15;
    public static final byte O_DEEPSLATE_LAPIS = 16;
    public static final byte O_DEEPSLATE_DIAMOND = 17;
    public static final byte O_DEEPSLATE_REDSTONE = 18;
    public static final byte O_DEEPSLATE_EMERALD = 19;

    private SectionGenerator() {
    }

    public static SectionBlob generate(GeneratorContext ctx, int cx, int sy, int cz, SeedOreMode mode) {
        NoiseRouter router = ctx.randomState().router();
        TrilinearSampler sampler = new TrilinearSampler(
                ctx.randomState(),
                router.finalDensity(),
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.lavaNoise(),
                cx, sy, cz
        );

        byte[] categories = new byte[16 * 16 * 16];
        int index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++, index++) {
                    double density = sampler.finalDensityAt(x, y, z);
                    if (density > 0.0D) {
                        categories[index] = SOLID;
                        continue;
                    }
                    TrilinearSampler.AquiferPick aquifer = sampler.aquiferAt(x, y, z);
                    if (aquifer.isLava()) {
                        categories[index] = LAVA;
                    } else if (aquifer.isWater()) {
                        categories[index] = WATER;
                    } else {
                        categories[index] = AIR;
                    }
                }
            }
        }

        if (mode == SeedOreMode.TERRAIN_PLUS_VEINS) {
            VeinMarker.fastFlagVeins(ctx.randomState().router(), cx, sy, cz, categories);
        } else if (mode == SeedOreMode.TERRAIN_PLUS_EXACT_ORES) {
            OrePlacementRunner.placeExactOres(ctx, cx, sy, cz, categories);
        }

        RLEWriter writer = new RLEWriter(256);
        SectionConnectivity connectivity = new SectionConnectivity(16, 16, 16);
        index = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++, index++) {
                    byte category = categories[index];
                    writer.put(category);
                    if (category == AIR || category == WATER) {
                        connectivity.unionIfPassable(x, y, z, true);
                    } else {
                        connectivity.unionIfPassable(x, y, z, false);
                    }
                }
            }
        }

        return new SectionBlob(
                cx,
                sy,
                cz,
                writer.finish(),
                writer.runCount(),
                connectivity.faceMask(),
                connectivity.parentsArray()
        );
    }
}
