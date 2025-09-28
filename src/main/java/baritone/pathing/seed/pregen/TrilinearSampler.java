package baritone.pathing.seed.pregen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;

final class TrilinearSampler {

    private final RandomState state;
    private final DensityFunction finalDensity;
    private final DensityFunction barrier;
    private final DensityFunction flood;
    private final DensityFunction lava;
    private final int baseX;
    private final int baseY;
    private final int baseZ;

    TrilinearSampler(RandomState state,
                     DensityFunction finalDensity,
                     DensityFunction barrier,
                     DensityFunction flood,
                     DensityFunction lava,
                     int chunkX,
                     int sectionY,
                     int chunkZ) {
        this.state = state;
        this.finalDensity = finalDensity;
        this.barrier = barrier;
        this.flood = flood;
        this.lava = lava;
        this.baseX = chunkX << 4;
        this.baseY = sectionY << 4;
        this.baseZ = chunkZ << 4;
    }

    double finalDensityAt(int x, int y, int z) {
        return interpolate(finalDensity, x, y, z);
    }

    AquiferPick aquiferAt(int x, int y, int z) {
        double lavaNoise = interpolate(lava, x, y, z);
        double floodNoise = interpolate(flood, x, y, z);
        double barrierNoise = interpolate(barrier, x, y, z);
        boolean lavaFlag = lavaNoise >= 0.3D && barrierNoise < 0.0D;
        boolean waterFlag = !lavaFlag && floodNoise > 0.0D;
        return new AquiferPick(waterFlag, lavaFlag);
    }

    private double interpolate(DensityFunction function, int x, int y, int z) {
        int latticeX = x >> 2;
        int latticeY = y >> 2;
        int latticeZ = z >> 2;
        double dx = (x & 3) / 4.0D;
        double dy = (y & 3) / 4.0D;
        double dz = (z & 3) / 4.0D;
        double c000 = sample(function, latticeX, latticeY, latticeZ);
        double c100 = sample(function, latticeX + 1, latticeY, latticeZ);
        double c010 = sample(function, latticeX, latticeY + 1, latticeZ);
        double c110 = sample(function, latticeX + 1, latticeY + 1, latticeZ);
        double c001 = sample(function, latticeX, latticeY, latticeZ + 1);
        double c101 = sample(function, latticeX + 1, latticeY, latticeZ + 1);
        double c011 = sample(function, latticeX, latticeY + 1, latticeZ + 1);
        double c111 = sample(function, latticeX + 1, latticeY + 1, latticeZ + 1);
        double c00 = c000 * (1.0D - dx) + c100 * dx;
        double c10 = c010 * (1.0D - dx) + c110 * dx;
        double c01 = c001 * (1.0D - dx) + c101 * dx;
        double c11 = c011 * (1.0D - dx) + c111 * dx;
        double c0 = c00 * (1.0D - dy) + c10 * dy;
        double c1 = c01 * (1.0D - dy) + c11 * dy;
        return c0 * (1.0D - dz) + c1 * dz;
    }

    private double sample(DensityFunction function, int latticeX, int latticeY, int latticeZ) {
        int worldX = baseX + (latticeX << 2);
        int worldY = baseY + (latticeY << 2);
        int worldZ = baseZ + (latticeZ << 2);
        DensityFunction.SinglePointContext context = new DensityFunction.SinglePointContext(worldX, worldY, worldZ);
        return function.compute(context);
    }

    record AquiferPick(boolean isWater, boolean isLava) {
    }
}
