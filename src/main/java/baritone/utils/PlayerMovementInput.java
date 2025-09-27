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

package baritone.utils;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.behavior.humanization.HumanizationProfileSnapshot;
import baritone.api.utils.input.Input;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

import java.util.Random;

public class PlayerMovementInput extends ClientInput {

    private final InputOverrideHandler handler;
    private final Random humanRandom = new Random();
    private float strafeNoise;
    private float forwardNoise;
    private float targetStrafeNoise;
    private float targetForwardNoise;
    private int noiseTicksRemaining;
    private boolean lastSprintRequest;
    private int sprintDelayTicks;
    private boolean lastJumpRequest;
    private int jumpDelayTicks;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        float leftImpulse = 0.0F;
        float forwardImpulse = 0.0F;
        boolean jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

        boolean up = handler.isInputForcedDown(Input.MOVE_FORWARD);
        if (up) {
            forwardImpulse++;
        }

        boolean down = handler.isInputForcedDown(Input.MOVE_BACK);
        if (down) {
            forwardImpulse--;
        }

        boolean left = handler.isInputForcedDown(Input.MOVE_LEFT);
        if (left) {
            leftImpulse++;
        }

        boolean right = handler.isInputForcedDown(Input.MOVE_RIGHT);
        if (right) {
            leftImpulse--;
        }

        boolean sneaking = handler.isInputForcedDown(Input.SNEAK);
        if (sneaking) {
            leftImpulse *= 0.3D;
            forwardImpulse *= 0.3D;
        }
        Vec2 movement = new Vec2(leftImpulse, forwardImpulse);

        final Settings settings = Baritone.settings();
        final boolean humanize = settings.antiCheatCompatibility.value && settings.antiCheatHumanization.value;

        final HumanizationProfileSnapshot snapshot = humanize
                ? handler.baritone.getHumanizationBehavior().snapshot()
                : HumanizationProfileSnapshot.EMPTY;

        if (humanize) {
            movement = applyMovementHumanization(movement, settings, snapshot);
            jumping = applyJumpHumanization(jumping, settings, snapshot);
        } else {
            resetMovementHumanization();
        }

        this.moveVector = movement;

        boolean sprinting = handler.isInputForcedDown(Input.SPRINT);
        if (humanize) {
            sprinting = applySprintHumanization(sprinting, settings, snapshot);
        } else {
            resetSprintHumanization();
        }

        this.keyPresses = new net.minecraft.world.entity.player.Input(up, down, left, right, jumping, sneaking, sprinting);
    }

    private Vec2 applyMovementHumanization(Vec2 base, Settings settings, HumanizationProfileSnapshot snapshot) {
        if (Math.abs(base.x) < 1.0E-3f && Math.abs(base.y) < 1.0E-3f) {
            fadeMovementNoise();
            return base;
        }

        if (this.noiseTicksRemaining-- <= 0) {
            final float baseMovement = Math.max(0.0f, settings.antiCheatHumanizationMovement.value);
            final float strafeAmplitude = resolveMovementAmplitude(baseMovement, snapshot.getStrafeDelta());
            final float forwardAmplitude = resolveMovementAmplitude(baseMovement, snapshot.getForwardDelta());
            this.targetStrafeNoise = randomInRange(strafeAmplitude);
            this.targetForwardNoise = randomInRange(forwardAmplitude);

            final int baseInterval = Math.max(1, settings.antiCheatHumanizationInterval.value);
            final int variance = Math.max(1, baseInterval);
            this.noiseTicksRemaining = baseInterval / 2 + this.humanRandom.nextInt(variance);
        }

        this.strafeNoise += (this.targetStrafeNoise - this.strafeNoise) * 0.4f;
        this.forwardNoise += (this.targetForwardNoise - this.forwardNoise) * 0.4f;

        final float strafe = clamp(base.x + this.strafeNoise, -1.0f, 1.0f);
        final float forward = clamp(base.y + this.forwardNoise, -1.0f, 1.0f);
        return new Vec2(strafe, forward);
    }

    private boolean applySprintHumanization(boolean sprinting, Settings settings, HumanizationProfileSnapshot snapshot) {
        if (sprinting) {
            if (!this.lastSprintRequest) {
                final int baseInterval = Math.max(1, settings.antiCheatHumanizationInterval.value / 2);
                this.sprintDelayTicks = sampleReactionDelay(snapshot.getSprintReactionTicks(), baseInterval, false);
            }
        } else {
            this.sprintDelayTicks = 0;
        }

        this.lastSprintRequest = sprinting;

        if (this.sprintDelayTicks > 0) {
            this.sprintDelayTicks--;
            return false;
        }

        return sprinting;
    }

    private boolean applyJumpHumanization(boolean jumping, Settings settings, HumanizationProfileSnapshot snapshot) {
        if (jumping) {
            if (!this.lastJumpRequest) {
                final int baseInterval = Math.max(0, settings.antiCheatHumanizationInterval.value / 2);
                this.jumpDelayTicks = sampleReactionDelay(snapshot.getJumpReactionTicks(), baseInterval, true);
            }
        } else {
            this.jumpDelayTicks = 0;
        }

        this.lastJumpRequest = jumping;

        if (this.jumpDelayTicks > 0) {
            this.jumpDelayTicks--;
            return false;
        }

        return jumping;
    }

    private void resetMovementHumanization() {
        fadeMovementNoise();
        this.noiseTicksRemaining = 0;
    }

    private void resetSprintHumanization() {
        this.lastSprintRequest = false;
        this.sprintDelayTicks = 0;
        this.lastJumpRequest = false;
        this.jumpDelayTicks = 0;
    }

    private void fadeMovementNoise() {
        this.targetStrafeNoise = 0.0f;
        this.targetForwardNoise = 0.0f;
        this.strafeNoise *= 0.6f;
        this.forwardNoise *= 0.6f;
        if (Math.abs(this.strafeNoise) < 1.0E-4f) {
            this.strafeNoise = 0.0f;
        }
        if (Math.abs(this.forwardNoise) < 1.0E-4f) {
            this.forwardNoise = 0.0f;
        }
    }

    private float randomInRange(float max) {
        if (max <= 0.0f) {
            return 0.0f;
        }
        return (float) ((this.humanRandom.nextDouble() * 2.0 - 1.0) * max);
    }

    private float resolveMovementAmplitude(float base, HumanizationProfileSnapshot.RunningStat stat) {
        float amplitude = base;
        if (stat.hasSamples()) {
            double target = stat.getMean() + stat.getStdDev() * 0.5;
            amplitude = Math.max(amplitude, (float) Math.min(0.75f, Math.abs(target)));
        }
        return amplitude;
    }

    private int sampleReactionDelay(HumanizationProfileSnapshot.RunningStat stat, int fallbackRange, boolean allowZero) {
        if (stat.hasSamples()) {
            double mean = Math.max(0.0, stat.getMean());
            double std = stat.getStdDev();
            double sample = std > 0.0 ? this.humanRandom.nextGaussian() * std + mean : mean;
            if (stat.getCount() < 3) {
                sample = mean;
            }
            return Math.max(0, (int) Math.round(sample));
        }
        if (fallbackRange <= 0) {
            return allowZero ? 0 : 0;
        }
        if (allowZero) {
            return this.humanRandom.nextInt(fallbackRange + 1);
        }
        return this.humanRandom.nextInt(Math.max(1, fallbackRange)) + 1;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
