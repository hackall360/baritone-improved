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

package baritone.humanization;

import baritone.api.behavior.humanization.HumanizationProfileSnapshot;

/**
 * Mutable container that aggregates player telemetry for the humanization layer.
 */
public final class HumanizationProfile {

    private final RunningStat movementMagnitude = new RunningStat();
    private final RunningStat strafeDelta = new RunningStat();
    private final RunningStat forwardDelta = new RunningStat();
    private final RunningStat yawDelta = new RunningStat();
    private final RunningStat pitchDelta = new RunningStat();
    private final RunningStat sprintReactionTicks = new RunningStat();
    private final RunningStat jumpReactionTicks = new RunningStat();
    private final RunningStat blockBreakDowntime = new RunningStat();
    private final RunningStat blockBreakDuration = new RunningStat();
    private final RunningStat blockPlaceInterval = new RunningStat();
    private long recordedTicks;

    public void addMovementSample(double strafe, double forward) {
        double magnitude = Math.sqrt(strafe * strafe + forward * forward);
        this.movementMagnitude.add(magnitude);
        this.strafeDelta.add(strafe);
        this.forwardDelta.add(forward);
    }

    public void addRotationSample(double yaw, double pitch) {
        this.yawDelta.add(yaw);
        this.pitchDelta.add(pitch);
    }

    public void addSprintReaction(int ticks) {
        if (ticks >= 0) {
            this.sprintReactionTicks.add(ticks);
        }
    }

    public void addJumpReaction(int ticks) {
        if (ticks >= 0) {
            this.jumpReactionTicks.add(ticks);
        }
    }

    public void addBlockBreakDowntime(long ticks) {
        if (ticks >= 0) {
            this.blockBreakDowntime.add(ticks);
        }
    }

    public void addBlockBreakDuration(long ticks) {
        if (ticks >= 0) {
            this.blockBreakDuration.add(ticks);
        }
    }

    public void addBlockPlaceInterval(long ticks) {
        if (ticks >= 0) {
            this.blockPlaceInterval.add(ticks);
        }
    }

    public void incrementRecordedTicks() {
        this.recordedTicks++;
    }

    public long getRecordedTicks() {
        return this.recordedTicks;
    }

    public void clear() {
        this.movementMagnitude.clear();
        this.strafeDelta.clear();
        this.forwardDelta.clear();
        this.yawDelta.clear();
        this.pitchDelta.clear();
        this.sprintReactionTicks.clear();
        this.jumpReactionTicks.clear();
        this.blockBreakDowntime.clear();
        this.blockBreakDuration.clear();
        this.blockPlaceInterval.clear();
        this.recordedTicks = 0L;
    }

    public HumanizationProfileSnapshot snapshot() {
        return new HumanizationProfileSnapshot(
                this.movementMagnitude.snapshot(),
                this.strafeDelta.snapshot(),
                this.forwardDelta.snapshot(),
                this.yawDelta.snapshot(),
                this.pitchDelta.snapshot(),
                this.sprintReactionTicks.snapshot(),
                this.jumpReactionTicks.snapshot(),
                this.blockBreakDowntime.snapshot(),
                this.blockBreakDuration.snapshot(),
                this.blockPlaceInterval.snapshot(),
                this.recordedTicks
        );
    }
}
