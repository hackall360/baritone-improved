package baritone.pathing.seed.pregen;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

import java.nio.file.Path;

public record GeneratorContext(
        long seed,
        RegistryAccess registries,
        Holder<NoiseGeneratorSettings> noiseSettings,
        RandomState randomState,
        Holder<DimensionType> dimensionType,
        ResourceKey<Level> dimensionKey,
        Path saveRoot
) {
}
