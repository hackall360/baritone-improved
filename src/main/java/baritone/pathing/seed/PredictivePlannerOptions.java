package baritone.pathing.seed;

/**
 * Configuration values for the predictive planner.
 */
public record PredictivePlannerOptions(
        int macroTileSize,
        double heuristicWeight,
        int maxIterations,
        boolean preferUnderground) {

    public static PredictivePlannerOptions defaults() {
        return new PredictivePlannerOptions(4, 1.5, 25000, false);
    }

    public PredictivePlannerOptions withPreferUnderground(boolean preferUnderground) {
        if (this.preferUnderground == preferUnderground) {
            return this;
        }
        return new PredictivePlannerOptions(macroTileSize, heuristicWeight, maxIterations, preferUnderground);
    }
}
