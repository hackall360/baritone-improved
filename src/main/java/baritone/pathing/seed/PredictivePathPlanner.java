package baritone.pathing.seed;

import baritone.api.utils.BetterBlockPos;
import baritone.cache.seed.RegionLayout;
import baritone.cache.seed.SeedSummaryManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/**
 * CPU-first macro planner that reasons about compressed seed summaries. It runs a weighted A*
 * search across macro tiles and defers expensive chunk decoding until a tile is expanded.
 */
public final class PredictivePathPlanner {

    private final SeedSummaryManager summaries;

    public PredictivePathPlanner(SeedSummaryManager summaries) {
        this.summaries = Objects.requireNonNull(summaries);
    }

    public PlannerResult plan(BetterBlockPos start, BetterBlockPos goal, PredictivePlannerOptions options) {
        options = options != null ? options : PredictivePlannerOptions.defaults();
        MacroGraph graph = new MacroGraph(summaries, options.macroTileSize());
        MacroNode startNode = graph.nodeFor(start);
        MacroNode goalNode = graph.nodeFor(goal);
        if (!graph.isPassable(goalNode)) {
            return PlannerResult.failed();
        }

        Queue<SearchNode> open = new PriorityQueue<>((a, b) -> Double.compare(a.fScore, b.fScore));
        Map<MacroNode, Double> gScores = new HashMap<>();
        Set<MacroNode> closed = new HashSet<>();

        SearchNode startSearch = new SearchNode(startNode, null, 0, heuristic(startNode, goalNode, options.heuristicWeight()));
        open.add(startSearch);
        gScores.put(startNode, 0.0);

        int iterations = 0;
        while (!open.isEmpty() && iterations++ < options.maxIterations()) {
            SearchNode current = open.poll();
            if (current.node.equals(goalNode)) {
                return PlannerResult.success(reconstruct(current, graph, goal));
            }
            if (!closed.add(current.node)) {
                continue;
            }
            for (MacroNode neighbor : graph.neighbors(current.node)) {
                if (!graph.isPassable(neighbor) || closed.contains(neighbor)) {
                    continue;
                }
                double tentativeG = current.gScore + graph.cost(current.node, neighbor);
                double existing = gScores.getOrDefault(neighbor, Double.POSITIVE_INFINITY);
                if (tentativeG >= existing) {
                    continue;
                }
                gScores.put(neighbor, tentativeG);
                double h = heuristic(neighbor, goalNode, options.heuristicWeight());
                open.add(new SearchNode(neighbor, current, tentativeG, tentativeG + h));
            }
        }
        return PlannerResult.failed();
    }

    private static double heuristic(MacroNode node, MacroNode goal, double weight) {
        double dx = Math.abs(node.tileX - goal.tileX);
        double dz = Math.abs(node.tileZ - goal.tileZ);
        return weight * (dx + dz);
    }

    private static List<BetterBlockPos> reconstruct(SearchNode node, MacroGraph graph, BetterBlockPos goal) {
        List<BetterBlockPos> result = new ArrayList<>();
        SearchNode cursor = node;
        while (cursor != null) {
            result.add(graph.tileCenter(cursor.node));
            cursor = cursor.parent;
        }
        result.set(0, goal);
        List<BetterBlockPos> reversed = new ArrayList<>(result.size());
        for (int i = result.size() - 1; i >= 0; i--) {
            reversed.add(result.get(i));
        }
        return reversed;
    }

    private record SearchNode(MacroNode node, SearchNode parent, double gScore, double fScore) {
    }

    private static final class MacroGraph {
        private final SeedSummaryManager summaries;
        private final int macroTileSize;

        private MacroGraph(SeedSummaryManager summaries, int macroTileSize) {
            this.summaries = summaries;
            this.macroTileSize = Math.max(1, macroTileSize);
        }

        MacroNode nodeFor(BetterBlockPos pos) {
            int chunkX = Math.floorDiv(pos.getX(), RegionLayout.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(pos.getZ(), RegionLayout.CHUNK_SIZE);
            int tileX = Math.floorDiv(chunkX, macroTileSize);
            int tileZ = Math.floorDiv(chunkZ, macroTileSize);
            return new MacroNode(tileX, tileZ);
        }

        boolean isPassable(MacroNode node) {
            for (int dz = 0; dz < macroTileSize; dz++) {
                for (int dx = 0; dx < macroTileSize; dx++) {
                    int chunkX = node.tileX * macroTileSize + dx;
                    int chunkZ = node.tileZ * macroTileSize + dz;
                    if (summaries.hasPassableSurface(chunkX, chunkZ)) {
                        return true;
                    }
                }
            }
            return false;
        }

        Iterable<MacroNode> neighbors(MacroNode node) {
            List<MacroNode> out = new ArrayList<>(4);
            out.add(new MacroNode(node.tileX + 1, node.tileZ));
            out.add(new MacroNode(node.tileX - 1, node.tileZ));
            out.add(new MacroNode(node.tileX, node.tileZ + 1));
            out.add(new MacroNode(node.tileX, node.tileZ - 1));
            return out;
        }

        double cost(MacroNode a, MacroNode b) {
            return a.tileX == b.tileX || a.tileZ == b.tileZ ? 1 : Math.sqrt(2);
        }

        BetterBlockPos tileCenter(MacroNode node) {
            int chunkX = node.tileX * macroTileSize + macroTileSize / 2;
            int chunkZ = node.tileZ * macroTileSize + macroTileSize / 2;
            int blockX = chunkX * RegionLayout.CHUNK_SIZE + RegionLayout.CHUNK_SIZE / 2;
            int blockZ = chunkZ * RegionLayout.CHUNK_SIZE + RegionLayout.CHUNK_SIZE / 2;
            int surface = summaries.topY(chunkX, chunkZ, RegionLayout.CHUNK_SIZE / 2, RegionLayout.CHUNK_SIZE / 2);
            return new BetterBlockPos(blockX, surface, blockZ);
        }
    }

    private record MacroNode(int tileX, int tileZ) {
    }

    public record PlannerResult(boolean success, List<BetterBlockPos> waypoints) {
        public static PlannerResult success(List<BetterBlockPos> waypoints) {
            return new PlannerResult(true, waypoints);
        }

        public static PlannerResult failed() {
            return new PlannerResult(false, List.of());
        }
    }
}
