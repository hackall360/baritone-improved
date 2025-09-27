package baritone.pathing.seed;

/**
 * Configuration values for the predictive planner.
 */
public record PredictivePlannerOptions(
        int macroTileSize,
        double heuristicWeight,
        int maxIterations) {

    public static PredictivePlannerOptions defaults() {
        return new PredictivePlannerOptions(4, 1.5, 25000);
    }
}
