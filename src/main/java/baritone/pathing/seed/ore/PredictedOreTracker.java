package baritone.pathing.seed.ore;

import baritone.api.utils.BlockOptionalMeta;
import baritone.api.utils.BlockOptionalMetaLookup;
import baritone.pathing.seed.pregen.SectionBlob;
import baritone.pathing.seed.pregen.SectionGenerator;
import baritone.pathing.seed.pregen.SectionUtil;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks predicted ore positions generated from seed pre-generation and maintains vein status information so Baritone can
 * avoid revisiting mined veins.
 */
public final class PredictedOreTracker {

    private final Map<ResourceKey<Level>, DimensionStore> stores = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, VeinStatus>> statusMemory = new ConcurrentHashMap<>();

    public void ingest(ResourceKey<Level> dimension, SectionBlob blob) {
        if (dimension == null || blob == null) {
            return;
        }
        stores.computeIfAbsent(dimension, key -> new DimensionStore(dimension)).ingest(blob);
    }

    public List<BlockPos> query(ResourceKey<Level> dimension, BlockOptionalMetaLookup filter, BlockPos origin, int max) {
        DimensionStore store = stores.get(dimension);
        if (store == null || filter == null) {
            return Collections.emptyList();
        }
        Set<Block> allowed = filter.blocks().stream().map(BlockOptionalMeta::getBlock).collect(Collectors.toSet());
        if (allowed.isEmpty()) {
            return Collections.emptyList();
        }
        return store.query(allowed, origin, max);
    }

    public void markVeinMining(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return;
        }
        DimensionStore store = stores.get(dimension);
        if (store != null) {
            store.markVeinMining(pos.asLong());
        }
    }

    public void markVeinMined(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return;
        }
        DimensionStore store = stores.get(dimension);
        if (store != null) {
            store.markVeinMined(pos.asLong());
        }
    }

    public void markVeinAvailable(ResourceKey<Level> dimension, BlockPos pos) {
        if (dimension == null || pos == null) {
            return;
        }
        DimensionStore store = stores.get(dimension);
        if (store != null) {
            store.markVeinAvailable(pos.asLong());
        }
    }

    public BlockState predictedState(ResourceKey<Level> dimension, BlockPos pos, BlockState fallback) {
        DimensionStore store = stores.get(dimension);
        if (store == null || pos == null) {
            return fallback;
        }
        return store.predictedState(pos.asLong(), fallback);
    }

    public void clear(ResourceKey<Level> dimension) {
        if (dimension == null) {
            return;
        }
        stores.remove(dimension);
        statusMemory.remove(dimension);
    }

    public void clearAll() {
        stores.clear();
        statusMemory.clear();
    }

    private VeinStatus rememberedStatus(ResourceKey<Level> dimension, long pos) {
        Map<Long, VeinStatus> status = statusMemory.get(dimension);
        if (status == null) {
            return null;
        }
        return status.get(pos);
    }

    private void rememberStatus(ResourceKey<Level> dimension, long pos, VeinStatus status) {
        statusMemory.computeIfAbsent(dimension, key -> new ConcurrentHashMap<>()).put(pos, status);
    }

    private static Block mapCategory(byte category) {
        return switch (category) {
            case SectionGenerator.O_COAL -> Blocks.COAL_ORE;
            case SectionGenerator.O_IRON -> Blocks.IRON_ORE;
            case SectionGenerator.O_COPPER -> Blocks.COPPER_ORE;
            case SectionGenerator.O_GOLD -> Blocks.GOLD_ORE;
            case SectionGenerator.O_LAPIS -> Blocks.LAPIS_ORE;
            case SectionGenerator.O_DIAMOND -> Blocks.DIAMOND_ORE;
            case SectionGenerator.O_REDSTONE -> Blocks.REDSTONE_ORE;
            case SectionGenerator.O_EMERALD -> Blocks.EMERALD_ORE;
            case SectionGenerator.O_DEEPSLATE_COAL -> Blocks.DEEPSLATE_COAL_ORE;
            case SectionGenerator.O_DEEPSLATE_IRON -> Blocks.DEEPSLATE_IRON_ORE;
            case SectionGenerator.O_DEEPSLATE_COPPER -> Blocks.DEEPSLATE_COPPER_ORE;
            case SectionGenerator.O_DEEPSLATE_GOLD -> Blocks.DEEPSLATE_GOLD_ORE;
            case SectionGenerator.O_DEEPSLATE_LAPIS -> Blocks.DEEPSLATE_LAPIS_ORE;
            case SectionGenerator.O_DEEPSLATE_DIAMOND -> Blocks.DEEPSLATE_DIAMOND_ORE;
            case SectionGenerator.O_DEEPSLATE_REDSTONE -> Blocks.DEEPSLATE_REDSTONE_ORE;
            case SectionGenerator.O_DEEPSLATE_EMERALD -> Blocks.DEEPSLATE_EMERALD_ORE;
            default -> null;
        };
    }

    private enum VeinStatus {
        UNMINED,
        MINING,
        MINED
    }

    private final class DimensionStore {

        private final Map<Long, PredictedVein> blockIndex = new ConcurrentHashMap<>();
        private final Map<Long, LongOpenHashSet> sectionIndex = new ConcurrentHashMap<>();
        private final Set<PredictedVein> veins = ConcurrentHashMap.newKeySet();
        private final ResourceKey<Level> dimension;

        private DimensionStore(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        private synchronized void ingest(SectionBlob blob) {
            long sectionKey = SectionUtil.key(blob.chunkX(), blob.sectionY(), blob.chunkZ());
            LongOpenHashSet previous = sectionIndex.remove(sectionKey);
            if (previous != null) {
                LongIterator iterator = previous.iterator();
                while (iterator.hasNext()) {
                    long pos = iterator.nextLong();
                    PredictedVein vein = blockIndex.remove(pos);
                    if (vein != null) {
                        vein.positions.remove(pos);
                        if (vein.positions.isEmpty()) {
                            veins.remove(vein);
                        }
                    }
                }
            }

            byte[] categories = decode(blob);
            LongOpenHashSet positions = new LongOpenHashSet();
            int index = 0;
            int baseX = blob.chunkX() << 4;
            int baseY = blob.sectionY() << 4;
            int baseZ = blob.chunkZ() << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++, index++) {
                        Block block = mapCategory(categories[index]);
                        if (block == null) {
                            continue;
                        }
                        long pos = BlockPos.asLong(baseX + x, baseY + y, baseZ + z);
                        addBlock(pos, block);
                        positions.add(pos);
                    }
                }
            }
            sectionIndex.put(sectionKey, positions);
        }

        private synchronized List<BlockPos> query(Set<Block> allowed, BlockPos origin, int max) {
            if (veins.isEmpty()) {
                return Collections.emptyList();
            }
            List<BlockPos> results = new ArrayList<>();
            Comparator<BlockPos> sorter = origin == null
                    ? Comparator.comparingDouble(BlockPos::getY)
                    : Comparator.comparingDouble(pos -> pos.distSqr(origin));
            for (PredictedVein vein : veins) {
                if (!allowed.contains(vein.block)) {
                    continue;
                }
                if (vein.status == VeinStatus.MINED || vein.status == VeinStatus.MINING || vein.positions.isEmpty()) {
                    continue;
                }
                LongIterator iterator = vein.positions.iterator();
                while (iterator.hasNext()) {
                    results.add(BlockPos.of(iterator.nextLong()));
                    if (max > 0 && results.size() >= max) {
                        break;
                    }
                }
                if (max > 0 && results.size() >= max) {
                    break;
                }
            }
            results.sort(sorter);
            if (max > 0 && results.size() > max) {
                return new ArrayList<>(results.subList(0, max));
            }
            return results;
        }

        private synchronized void markVeinMining(long pos) {
            PredictedVein vein = blockIndex.get(pos);
            if (vein != null && vein.status == VeinStatus.UNMINED) {
                vein.status = VeinStatus.MINING;
            }
        }

        private synchronized void markVeinMined(long pos) {
            PredictedVein vein = blockIndex.get(pos);
            if (vein == null) {
                return;
            }
            vein.status = VeinStatus.MINED;
            LongIterator iterator = vein.positions.iterator();
            while (iterator.hasNext()) {
                long value = iterator.nextLong();
                rememberStatus(dimension, value, VeinStatus.MINED);
            }
        }

        private synchronized void markVeinAvailable(long pos) {
            PredictedVein vein = blockIndex.get(pos);
            if (vein == null || vein.status == VeinStatus.MINED) {
                return;
            }
            vein.status = VeinStatus.UNMINED;
        }

        private synchronized BlockState predictedState(long pos, BlockState fallback) {
            PredictedVein vein = blockIndex.get(pos);
            if (vein == null || vein.positions.isEmpty() || vein.status == VeinStatus.MINED) {
                return fallback;
            }
            return vein.block.defaultBlockState();
        }

        private void addBlock(long pos, Block block) {
            PredictedVein target = null;
            List<PredictedVein> toMerge = null;
            for (long neighbor : neighbors(pos)) {
                PredictedVein neighborVein = blockIndex.get(neighbor);
                if (neighborVein != null && neighborVein.block == block) {
                    if (target == null) {
                        target = neighborVein;
                    } else if (target != neighborVein) {
                        if (toMerge == null) {
                            toMerge = new ArrayList<>();
                        }
                        if (!toMerge.contains(neighborVein)) {
                            toMerge.add(neighborVein);
                        }
                    }
                }
            }
            if (target == null) {
                target = new PredictedVein(block);
                veins.add(target);
            }
            target.positions.add(pos);
            blockIndex.put(pos, target);
            if (toMerge != null) {
                for (PredictedVein other : toMerge) {
                    mergeVeins(target, other);
                }
            }
            VeinStatus remembered = rememberedStatus(dimension, pos);
            if (remembered == VeinStatus.MINED) {
                target.status = VeinStatus.MINED;
            }
        }

        private void mergeVeins(PredictedVein target, PredictedVein other) {
            if (target == other) {
                return;
            }
            target.status = mergeStatus(target.status, other.status);
            LongIterator iterator = other.positions.iterator();
            while (iterator.hasNext()) {
                long value = iterator.nextLong();
                target.positions.add(value);
                blockIndex.put(value, target);
            }
            veins.remove(other);
        }

        private List<Long> neighbors(long pos) {
            List<Long> neighborList = new ArrayList<>(6);
            BlockPos base = BlockPos.of(pos);
            neighborList.add(base.above().asLong());
            neighborList.add(base.below().asLong());
            neighborList.add(base.north().asLong());
            neighborList.add(base.south().asLong());
            neighborList.add(base.east().asLong());
            neighborList.add(base.west().asLong());
            return neighborList;
        }

        private VeinStatus mergeStatus(VeinStatus first, VeinStatus second) {
            if (first == VeinStatus.MINED || second == VeinStatus.MINED) {
                return VeinStatus.MINED;
            }
            if (first == VeinStatus.MINING || second == VeinStatus.MINING) {
                return VeinStatus.MINING;
            }
            return VeinStatus.UNMINED;
        }

        private byte[] decode(SectionBlob blob) {
            byte[] rle = blob.rleData();
            byte[] values = new byte[16 * 16 * 16];
            int index = 0;
            int offset = 0;
            for (int run = 0; run < blob.runCount() && index < values.length; run++) {
                int value = rle[offset++] & 0xFF;
                int length = 0;
                int shift = 0;
                while (true) {
                    int b = rle[offset++] & 0xFF;
                    length |= (b & 0x7F) << shift;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift += 7;
                }
                for (int i = 0; i < length && index < values.length; i++) {
                    values[index++] = (byte) value;
                }
            }
            return values;
        }
    }

    private static final class PredictedVein {
        private final Block block;
        private final LongOpenHashSet positions = new LongOpenHashSet();
        private VeinStatus status = VeinStatus.UNMINED;

        private PredictedVein(Block block) {
            this.block = Objects.requireNonNull(block, "block");
        }
    }
}
