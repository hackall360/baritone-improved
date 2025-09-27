package baritone.cache.seed;

import baritone.api.Settings;

/**
 * Aggregated surface metrics used to influence predictive planning.
 */
public record SurfaceMetrics(
        double passableRatio,
        int minY,
        int maxY,
        double averageY,
        boolean hasCave,
        boolean likelyWater,
        boolean likelyLava,
        boolean likelyHole,
        double waterCoverage,
        double lavaCoverage,
        double hostileDensity) {

    public static final SurfaceMetrics EMPTY = new SurfaceMetrics(0.0, 0, 0, 0.0, false, false, false, false, 0.0, 0.0, 0.0);

    public boolean isEmpty() {
        return passableRatio <= 0.0
                && minY == 0
                && maxY == 0
                && !hasCave
                && !likelyWater
                && !likelyLava
                && !likelyHole
                && waterCoverage <= 0.0
                && lavaCoverage <= 0.0
                && hostileDensity <= 0.0;
    }

    public double traversalCost(Settings settings, boolean preferUnderground) {
        double passable = clamp01(passableRatio);
        if (passable <= 0.01) {
            return preferUnderground && hasCave ? 3.0 : 4.5;
        }

        double cost = 1.0;
        cost += (1.0 - passable) * 2.5;

        double effectiveWater = waterCoverage > 0.0 ? clamp01(waterCoverage) : (likelyWater ? 0.6 : 0.0);
        if (effectiveWater > 0.0) {
            double basePenalty = settings.assumeWalkOnWater.value ? 0.2 : Math.max(0.6, settings.walkOnWaterOnePenalty.value / 2.5);
            if (preferUnderground) {
                basePenalty *= 0.7;
            }
            cost += effectiveWater * basePenalty;
        }

        double effectiveLava = lavaCoverage > 0.0 ? clamp01(lavaCoverage) : (likelyLava ? 0.35 : 0.0);
        if (effectiveLava > 0.0) {
            double lavaPenalty = settings.assumeWalkOnLava.value ? 0.8 : 2.5 + effectiveLava * 2.5;
            if (preferUnderground) {
                lavaPenalty *= 0.5;
            }
            cost += lavaPenalty;
        }

        if (likelyHole && !preferUnderground) {
            double holePenalty = settings.allowParkour.value ? 0.45 : 1.0;
            if (!settings.allowWaterBucketFall.value) {
                holePenalty += 0.35;
            }
            cost += holePenalty;
        }

        if (hasCave) {
            cost += preferUnderground ? -0.4 : 0.55;
        } else if (preferUnderground) {
            cost += 0.35;
        }

        int verticalSpan = maxY - minY;
        if (verticalSpan >= 12) {
            double spanPenalty = settings.allowWaterBucketFall.value ? 0.25 : 0.7;
            if (preferUnderground) {
                spanPenalty *= 0.7;
            }
            cost += spanPenalty;
        }

        if (hostileDensity > 0.0) {
            double mobPenalty = Math.min(3.25, hostileDensity * 2.6);
            if (settings.allowParkour.value) {
                mobPenalty *= 0.9;
            }
            if (settings.allowVines.value) {
                mobPenalty *= 0.95;
            }
            cost += mobPenalty;
        }

        if (!preferUnderground && effectiveWater < 0.1 && passable > 0.65 && settings.allowSprint.value) {
            cost -= 0.15;
        }

        return clamp(cost, 0.25, 5.0);
    }

    public boolean isViable(Settings settings, boolean preferUnderground) {
        double passable = clamp01(passableRatio);
        if (passable <= 0.01) {
            return false;
        }
        double effectiveLava = lavaCoverage > 0.0 ? clamp01(lavaCoverage) : (likelyLava ? 0.35 : 0.0);
        if (!preferUnderground && effectiveLava > 0.2 && !settings.assumeWalkOnLava.value) {
            return false;
        }
        if (!preferUnderground && hostileDensity > 3.5) {
            return false;
        }
        return true;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
