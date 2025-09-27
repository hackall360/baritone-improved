/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.api.behavior.humanization;

/**
 * Immutable summary of the recorded humanization statistics.
 */
public final class HumanizationProfileSnapshot {

    public static final HumanizationProfileSnapshot EMPTY = new HumanizationProfileSnapshot(
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            RunningStat.EMPTY,
            0L
    );

    private final RunningStat movementMagnitude;
    private final RunningStat strafeDelta;
    private final RunningStat forwardDelta;
    private final RunningStat yawDelta;
    private final RunningStat pitchDelta;
    private final RunningStat sprintReactionTicks;
    private final RunningStat jumpReactionTicks;
    private final RunningStat blockBreakDowntime;
    private final RunningStat blockBreakDuration;
    private final RunningStat blockPlaceInterval;
    private final long recordedTicks;

    public HumanizationProfileSnapshot(
            RunningStat movementMagnitude,
            RunningStat strafeDelta,
            RunningStat forwardDelta,
            RunningStat yawDelta,
            RunningStat pitchDelta,
            RunningStat sprintReactionTicks,
            RunningStat jumpReactionTicks,
            RunningStat blockBreakDowntime,
            RunningStat blockBreakDuration,
            RunningStat blockPlaceInterval,
            long recordedTicks
    ) {
        this.movementMagnitude = movementMagnitude;
        this.strafeDelta = strafeDelta;
        this.forwardDelta = forwardDelta;
        this.yawDelta = yawDelta;
        this.pitchDelta = pitchDelta;
        this.sprintReactionTicks = sprintReactionTicks;
        this.jumpReactionTicks = jumpReactionTicks;
        this.blockBreakDowntime = blockBreakDowntime;
        this.blockBreakDuration = blockBreakDuration;
        this.blockPlaceInterval = blockPlaceInterval;
        this.recordedTicks = recordedTicks;
    }

    public RunningStat getMovementMagnitude() {
        return this.movementMagnitude;
    }

    public RunningStat getStrafeDelta() {
        return this.strafeDelta;
    }

    public RunningStat getForwardDelta() {
        return this.forwardDelta;
    }

    public RunningStat getYawDelta() {
        return this.yawDelta;
    }

    public RunningStat getPitchDelta() {
        return this.pitchDelta;
    }

    public RunningStat getSprintReactionTicks() {
        return this.sprintReactionTicks;
    }

    public RunningStat getJumpReactionTicks() {
        return this.jumpReactionTicks;
    }

    public RunningStat getBlockBreakDowntime() {
        return this.blockBreakDowntime;
    }

    public RunningStat getBlockBreakDuration() {
        return this.blockBreakDuration;
    }

    public RunningStat getBlockPlaceInterval() {
        return this.blockPlaceInterval;
    }

    public long getRecordedTicks() {
        return this.recordedTicks;
    }

    public boolean hasMovementSamples() {
        return this.movementMagnitude.hasSamples() || this.strafeDelta.hasSamples() || this.forwardDelta.hasSamples();
    }

    public boolean hasRotationSamples() {
        return this.yawDelta.hasSamples() || this.pitchDelta.hasSamples();
    }

    public boolean hasSprintSamples() {
        return this.sprintReactionTicks.hasSamples();
    }

    public boolean hasJumpSamples() {
        return this.jumpReactionTicks.hasSamples();
    }

    public boolean hasBlockBreakSamples() {
        return this.blockBreakDuration.hasSamples();
    }

    public boolean hasBlockPlaceSamples() {
        return this.blockPlaceInterval.hasSamples();
    }

    /**
     * Immutable summary of a running statistic.
     */
    public static final class RunningStat {

        private static final RunningStat EMPTY = new RunningStat(0L, 0.0, 0.0, Double.NaN, Double.NaN);

        private final long samples;
        private final double mean;
        private final double stdDev;
        private final double min;
        private final double max;

        public RunningStat(long samples, double mean, double stdDev, double min, double max) {
            this.samples = samples;
            this.mean = mean;
            this.stdDev = stdDev;
            this.min = min;
            this.max = max;
        }

        public long getCount() {
            return this.samples;
        }

        public double getMean() {
            return this.mean;
        }

        public double getStdDev() {
            return this.stdDev;
        }

        public double getMin() {
            return this.min;
        }

        public double getMax() {
            return this.max;
        }

        public boolean hasSamples() {
            return this.samples > 0L;
        }
    }
}
