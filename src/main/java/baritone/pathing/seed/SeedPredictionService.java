package baritone.pathing.seed;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.pathing.seed.ISeedPathing;
import baritone.api.pathing.seed.SeedOreMode;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.interfaces.IGoalRenderPos;
import baritone.api.event.events.ChunkEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.cache.seed.DeflateBlockCompressor;
import baritone.cache.seed.RegionLayout;
import baritone.cache.seed.SeedSummaryManager;
import baritone.cache.seed.SurfaceMetrics;
import baritone.pathing.seed.pregen.GeneratorContext;
import baritone.pathing.seed.pregen.SectionPregenQueue;
import baritone.pathing.seed.pregen.SectionStore;
import baritone.pathing.movement.CalculationContext;
import baritone.utils.BlockStateInterface;
import baritone.utils.pathing.Favoring;
import baritone.pathing.seed.ore.PredictedOreTracker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static baritone.pathing.movement.MovementHelper.canWalkOn;
import static baritone.pathing.movement.MovementHelper.canWalkThrough;

/**
 * Coordinates seed-based predictive planning and observed chunk overrides.
 */
public final class SeedPredictionService implements ISeedPathing, AbstractGameEventListener {

    private final Baritone baritone;
    private final SeedSummaryManager summaries;
    private final PredictivePathPlanner planner;
    private final AtomicReference<PlannerCache> cache = new AtomicReference<>();

    private volatile boolean enabled;
    private volatile Long configuredSeed;
    private final ExecutorService pregenExecutor;
    private volatile GeneratorContext generatorContext;
    private volatile SectionStore sectionStore;
    private volatile SeedOreMode oreMode;
    private volatile int pregenRadius;
    private final PredictedOreTracker oreTracker = new PredictedOreTracker();

    public SeedPredictionService(Baritone baritone) {
        this.baritone = baritone;
        this.summaries = new SeedSummaryManager(new DeflateBlockCompressor());
        Settings settings = Baritone.settings();
        this.planner = new PredictivePathPlanner(summaries, settings);
        this.pregenExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
        if (settings.seedPredictionSeedConfigured.value) {
            this.configuredSeed = settings.seedPredictionSeed.value;
        }
        this.enabled = settings.seedBasedPrediction.value && configuredSeed != null;
        this.oreMode = settings.seedPredictionOreMode.value;
        this.pregenRadius = clampRadius(settings.seedPredictionPregenRadius.value);
        SectionPregenQueue.init(pregenExecutor, null, null);
    }

    @Override
    public OptionalLong seed() {
        Long value = configuredSeed;
        return value != null ? OptionalLong.of(value) : OptionalLong.empty();
    }

    @Override
    public boolean hasSeed() {
        return configuredSeed != null;
    }

    @Override
    public void setSeed(long seed) {
        this.configuredSeed = seed;
        Settings settings = Baritone.settings();
        settings.seedPredictionSeed.value = seed;
        settings.seedPredictionSeedConfigured.value = true;
        summaries.clearAll();
        cache.set(null);
        oreTracker.clearAll();
        if (settings.seedBasedPrediction.value) {
            this.enabled = true;
        }
        resetPregen();
    }

    @Override
    public void clearSeed() {
        configuredSeed = null;
        Settings settings = Baritone.settings();
        settings.seedPredictionSeedConfigured.value = false;
        summaries.clearAll();
        cache.set(null);
        enabled = false;
        settings.seedBasedPrediction.value = false;
        oreTracker.clearAll();
        resetPregen();
    }

    @Override
    public boolean isPredictionEnabled() {
        return enabled && configuredSeed != null;
    }

    @Override
    public boolean setPredictionEnabled(boolean enabled) {
        if (enabled && configuredSeed == null) {
            this.enabled = false;
            Baritone.settings().seedBasedPrediction.value = false;
            return false;
        }
        this.enabled = enabled;
        Baritone.settings().seedBasedPrediction.value = enabled;
        if (!enabled) {
            cache.set(null);
            resetPregen();
        }
        return isPredictionEnabled();
    }

    @Override
    public int pregenRadius() {
        return pregenRadius;
    }

    @Override
    public void setPregenRadius(int radius) {
        int clamped = clampRadius(radius);
        this.pregenRadius = clamped;
        Baritone.settings().seedPredictionPregenRadius.value = clamped;
        SectionPregenQueue.init(pregenExecutor, sectionStore, generatorContext != null ? this::handleGeneratedSection : null);
    }

    @Override
    public SeedOreMode generationMode() {
        return oreMode;
    }

    @Override
    public void setGenerationMode(SeedOreMode mode) {
        this.oreMode = mode != null ? mode : SeedOreMode.TERRAIN_ONLY;
        Baritone.settings().seedPredictionOreMode.value = this.oreMode;
        oreTracker.clearAll();
        SectionPregenQueue.init(pregenExecutor, sectionStore, generatorContext != null ? this::handleGeneratedSection : null);
    }

    public void applySeedFavoring(BetterBlockPos start, Goal goal, Favoring favoring, CalculationContext context, IPath previous) {
        if (!isPredictionEnabled()) {
            return;
        }
        BetterBlockPos goalPos = resolveGoal(goal, start);
        if (goalPos == null) {
            return;
        }
        boolean preferUnderground = shouldPreferUnderground(start, goalPos);
        List<BetterBlockPos> waypoints = planMacroPath(start, goalPos, preferUnderground);
        if (waypoints.isEmpty()) {
            return;
        }
        for (int i = 1; i < waypoints.size(); i++) {
            BetterBlockPos waypoint = waypoints.get(i);
            favoring.multiply(BetterBlockPos.longHash(waypoint), 0.9);
        }
        biasChunksAlongPath(favoring, waypoints, preferUnderground);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN || event.getState() != EventState.POST) {
            return;
        }
        tickPregen();
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        AbstractGameEventListener.super.onWorldEvent(event);
        if (event.getState() == EventState.POST) {
            resetPregen();
        }
    }

    @Override
    public void onChunkEvent(ChunkEvent event) {
        if (event.getState() != EventState.POST) {
            return;
        }
        if (event.getType() == ChunkEvent.Type.UNLOAD) {
            summaries.overrideSurfaceMetrics(event.getX(), event.getZ(), null);
            return;
        }
        if (event.getType() == ChunkEvent.Type.LOAD || event.getType().isPopulate()) {
            Level world = baritone.getPlayerContext().world();
            if (world == null) {
                return;
            }
            LevelChunk chunk = world.getChunk(event.getX(), event.getZ());
            SurfaceMetrics metrics = analyzeChunk(world, chunk);
            if (metrics != null) {
                summaries.overrideSurfaceMetrics(event.getX(), event.getZ(), metrics);
            }
        }
    }

    private void tickPregen() {
        if (!isPredictionEnabled()) {
            return;
        }
        Long seed = configuredSeed;
        if (seed == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            return;
        }
        ensureGeneratorContext(seed, level);
        if (generatorContext == null || sectionStore == null) {
            return;
        }
        int chunkX = minecraft.player.getBlockX() >> 4;
        int chunkZ = minecraft.player.getBlockZ() >> 4;
        SectionPregenQueue.queueAround(generatorContext, chunkX, chunkZ, pregenRadius, oreMode);
    }

    private void ensureGeneratorContext(long seed, ClientLevel level) {
        if (generatorContext != null) {
            return;
        }
        RegistryAccess registries = level.registryAccess();
        Registry<NoiseGeneratorSettings> noiseSettings = registries.lookupOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings settingsValue = noiseSettings.getOptional(NoiseGeneratorSettings.OVERWORLD)
                .or(() -> noiseSettings.stream().findFirst())
                .orElse(null);
        if (settingsValue == null) {
            return;
        }
        Holder<NoiseGeneratorSettings> holder = Holder.direct(settingsValue);
        RandomState randomState = RandomState.create(settingsValue, registries.lookupOrThrow(Registries.NOISE), seed);
        Path saveRoot = resolveSaveRoot(level);
        generatorContext = new GeneratorContext(seed, registries, holder, randomState,
                level.dimensionTypeRegistration(), level.dimension(), saveRoot);
        sectionStore = new SectionStore(saveRoot);
        oreTracker.clear(level.dimension());
        SectionPregenQueue.init(pregenExecutor, sectionStore, this::handleGeneratedSection);
    }

    private Path resolveSaveRoot(ClientLevel level) {
        Minecraft minecraft = Minecraft.getInstance();
        ServerData server = minecraft.getCurrentServer();
        String serverId = server != null ? server.ip : "singleplayer";
        serverId = serverId.replace(':', '_');
        String dimensionId = level.dimension().location().toString().replace(':', '_');
        return minecraft.gameDirectory.toPath()
                .resolve("baritone")
                .resolve("seed-pregen")
                .resolve(serverId)
                .resolve(dimensionId);
    }

    private void resetPregen() {
        GeneratorContext previous = generatorContext;
        if (previous != null) {
            oreTracker.clear(previous.dimensionKey());
        }
        generatorContext = null;
        sectionStore = null;
        SectionPregenQueue.init(pregenExecutor, null, null);
    }

    private int clampRadius(int radius) {
        return Mth.clamp(radius, 0, 32);
    }

    private List<BetterBlockPos> planMacroPath(BetterBlockPos start, BetterBlockPos goal, boolean preferUnderground) {
        PlannerCache cached = cache.get();
        if (cached != null && cached.matches(start, goal, preferUnderground)) {
            return cached.waypoints();
        }
        PredictivePlannerOptions options = PredictivePlannerOptions.defaults().withPreferUnderground(preferUnderground);
        PredictivePathPlanner.PlannerResult result = planner.plan(start, goal, options);
        if (!result.success()) {
            cache.set(null);
            return Collections.emptyList();
        }
        List<BetterBlockPos> waypoints = new ArrayList<>(result.waypoints());
        cache.set(new PlannerCache(start, goal, preferUnderground, waypoints));
        return waypoints;
    }

    private BetterBlockPos resolveGoal(Goal goal, BetterBlockPos startFallback) {
        if (goal instanceof IGoalRenderPos renderable) {
            return new BetterBlockPos(renderable.getGoalPos());
        }
        if (goal instanceof GoalXZ goalXZ) {
            return new BetterBlockPos(goalXZ.getX(), startFallback.getY(), goalXZ.getZ());
        }
        if (goal instanceof GoalYLevel yLevel) {
            return new BetterBlockPos(startFallback.getX(), yLevel.level, startFallback.getZ());
        }
        return null;
    }

    private boolean shouldPreferUnderground(BetterBlockPos start, BetterBlockPos goal) {
        SurfaceMetrics startMetrics = summaries.metrics(start.getX() >> 4, start.getZ() >> 4);
        SurfaceMetrics goalMetrics = summaries.metrics(goal.getX() >> 4, goal.getZ() >> 4);
        boolean startUnderground = startMetrics != SurfaceMetrics.EMPTY && start.getY() < startMetrics.averageY() - 3;
        boolean goalUnderground = goalMetrics != SurfaceMetrics.EMPTY && goal.getY() < goalMetrics.averageY() - 3;
        return startUnderground || goalUnderground;
    }

    private void biasChunksAlongPath(Favoring favoring, List<BetterBlockPos> waypoints, boolean preferUnderground) {
        Settings settings = Baritone.settings();
        LongOpenHashSet visited = new LongOpenHashSet();
        for (int i = 1; i < waypoints.size(); i++) {
            BetterBlockPos from = waypoints.get(i - 1);
            BetterBlockPos to = waypoints.get(i);
            walkChunks(from, to, visited, (chunkX, chunkZ) -> {
                SurfaceMetrics metrics = summaries.metrics(chunkX, chunkZ);
                if (metrics == SurfaceMetrics.EMPTY) {
                    return;
                }
                double multiplier = metrics.traversalCost(settings, preferUnderground);
                multiplier = Math.max(0.35, Math.min(multiplier, 4.0));
                int blockX = chunkX * RegionLayout.CHUNK_SIZE + RegionLayout.CHUNK_SIZE / 2;
                int blockZ = chunkZ * RegionLayout.CHUNK_SIZE + RegionLayout.CHUNK_SIZE / 2;
                int y = (int) Math.round(metrics.averageY());
                favoring.multiply(BetterBlockPos.longHash(blockX, y, blockZ), multiplier);
            });
        }
    }

    private void handleGeneratedSection(baritone.pathing.seed.pregen.SectionBlob blob) {
        GeneratorContext context = generatorContext;
        if (context == null) {
            return;
        }
        if (oreMode != SeedOreMode.TERRAIN_PLUS_EXACT_ORES) {
            return;
        }
        oreTracker.ingest(context.dimensionKey(), blob);
    }

    public List<BlockPos> predictedOreTargets(Level level, baritone.api.utils.BlockOptionalMetaLookup filter, BlockPos origin, int max) {
        if (!isPredictionEnabled() || level == null) {
            return Collections.emptyList();
        }
        return oreTracker.query(level.dimension(), filter, origin, max);
    }

    public void markVeinMining(Level level, BlockPos pos) {
        if (!isPredictionEnabled() || level == null || pos == null) {
            return;
        }
        oreTracker.markVeinMining(level.dimension(), pos);
    }

    public void markVeinMined(Level level, BlockPos pos) {
        if (!isPredictionEnabled() || level == null || pos == null) {
            return;
        }
        oreTracker.markVeinMined(level.dimension(), pos);
    }

    public void markVeinAvailable(Level level, BlockPos pos) {
        if (!isPredictionEnabled() || level == null || pos == null) {
            return;
        }
        oreTracker.markVeinAvailable(level.dimension(), pos);
    }

    public BlockState predictedBlockState(Level level, BlockPos pos, BlockState fallback) {
        if (!isPredictionEnabled() || level == null || pos == null) {
            return fallback;
        }
        return oreTracker.predictedState(level.dimension(), pos, fallback);
    }

    private SurfaceMetrics analyzeChunk(Level world, LevelChunk chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return null;
        }
        BlockStateInterface bsi = new BlockStateInterface(baritone.getPlayerContext());
        int total = 0;
        int passable = 0;
        int water = 0;
        int lava = 0;
        int cave = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        double sumY = 0.0;
        int minBuildHeight = world.dimensionType().minY();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int topY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ);
                if (topY <= minBuildHeight) {
                    continue;
                }
                int worldX = chunk.getPos().getMinBlockX() + localX;
                int worldZ = chunk.getPos().getMinBlockZ() + localZ;
                BlockPos surfacePos = new BlockPos(worldX, topY - 1, worldZ);
                BlockState surface = chunk.getBlockState(surfacePos);
                BlockState head = chunk.getBlockState(surfacePos.above());
                boolean canStand = canWalkOn(bsi, worldX, surfacePos.getY(), worldZ, surface)
                        && canWalkThrough(bsi, worldX, surfacePos.getY() + 1, worldZ, head);
                if (canStand) {
                    passable++;
                }
                total++;
                int y = surfacePos.getY();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sumY += y;
                FluidState surfaceFluid = surface.getFluidState();
                if (!surfaceFluid.isEmpty()) {
                    if (surfaceFluid.is(FluidTags.WATER)) {
                        water++;
                    } else if (surfaceFluid.is(FluidTags.LAVA)) {
                        lava++;
                    }
                }
                FluidState headFluid = head.getFluidState();
                if (!headFluid.isEmpty()) {
                    if (headFluid.is(FluidTags.WATER)) {
                        water++;
                    } else if (headFluid.is(FluidTags.LAVA)) {
                        lava++;
                    }
                }
                int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, localX, localZ);
                if (y - floor > 4) {
                    cave++;
                }
            }
        }
        if (total == 0) {
            return null;
        }
        ChunkPos pos = chunk.getPos();
        double minX = pos.getMinBlockX();
        double minZ = pos.getMinBlockZ();
        double maxX = pos.getMaxBlockX() + 1;
        double maxZ = pos.getMaxBlockZ() + 1;
        double minBoxY = minBuildHeight;
        double maxBoxY = minBuildHeight + world.dimensionType().height();
        List<Mob> hostiles = world.getEntitiesOfClass(Mob.class,
                new AABB(minX, minBoxY, minZ, maxX, maxBoxY, maxZ),
                mob -> mob.isAlive() && mob.getType().getCategory() == MobCategory.MONSTER);
        double hostileDensity = hostiles.size() / 5.0;
        double ratio = passable / (double) total;
        double average = sumY / total;
        boolean likelyHole = maxY - minY >= 6;
        boolean likelyWater = water > total * 0.2;
        boolean likelyLava = lava > 0;
        boolean hasCave = cave > total * 0.15;
        double waterRatio = water / (double) total;
        double lavaRatio = lava / (double) total;
        return new SurfaceMetrics(ratio, minY, maxY, average, hasCave, likelyWater, likelyLava, likelyHole, waterRatio, lavaRatio, hostileDensity);
    }

    private void walkChunks(BetterBlockPos from, BetterBlockPos to, LongOpenHashSet visited, ChunkVisitor visitor) {
        int x0 = from.getX() >> 4;
        int z0 = from.getZ() >> 4;
        int x1 = to.getX() >> 4;
        int z1 = to.getZ() >> 4;
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;
        int cx = x0;
        int cz = z0;
        while (true) {
            long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
            if (visited.add(key)) {
                visitor.visit(cx, cz);
            }
            if (cx == x1 && cz == z1) {
                break;
            }
            int e2 = err << 1;
            if (e2 > -dz) {
                err -= dz;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cz += sz;
            }
        }
    }

    private record PlannerCache(BetterBlockPos start, BetterBlockPos goal, boolean preferUnderground, List<BetterBlockPos> waypoints) {
        boolean matches(BetterBlockPos newStart, BetterBlockPos newGoal, boolean preferUnderground) {
            return this.preferUnderground == preferUnderground
                    && start.equals(newStart)
                    && goal.equals(newGoal);
        }
    }

    @FunctionalInterface
    private interface ChunkVisitor {
        void visit(int chunkX, int chunkZ);
    }
}
