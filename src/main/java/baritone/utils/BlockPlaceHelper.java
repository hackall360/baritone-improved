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
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.behavior.humanization.HumanizationProfileSnapshot;
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class BlockPlaceHelper {
    // base ticks between places caused by tick logic
    private static final int BASE_PLACE_DELAY = 1;

    private final IPlayerContext ctx;
    private int rightClickTimer;
    private final Random humanRandom = new Random();

    BlockPlaceHelper(IPlayerContext playerContext) {
        this.ctx = playerContext;
    }

    public void tick(boolean rightClickRequested) {
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = ctx.objectMouseOver();
        if (!rightClickRequested || ctx.player().isHandsBusy() || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }
        rightClickTimer = Baritone.settings().rightClickSpeed.value - BASE_PLACE_DELAY;
        rightClickTimer += sampleHumanPlaceDelay();
        for (InteractionHand hand : InteractionHand.values()) {
            if (ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, (BlockHitResult) mouseOver) == InteractionResult.SUCCESS) {
                ctx.player().swing(hand);
                return;
            }
            if (!ctx.player().getItemInHand(hand).isEmpty() && ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand) == InteractionResult.SUCCESS) {
                return;
            }
        }
    }

    private int sampleHumanPlaceDelay() {
        if (!shouldHumanize()) {
            return 0;
        }
        final IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(ctx.player());
        if (baritone == null) {
            return 0;
        }
        final HumanizationProfileSnapshot snapshot = baritone.getHumanizationBehavior().snapshot();
        final HumanizationProfileSnapshot.RunningStat interval = snapshot.getBlockPlaceInterval();
        if (!interval.hasSamples()) {
            return 0;
        }
        double mean = Math.max(0.0, interval.getMean());
        double std = interval.getStdDev();
        double sample = std > 0.0 ? humanRandom.nextGaussian() * std + mean : mean;
        if (interval.getCount() < 3) {
            sample = mean;
        }
        return Math.max(0, (int) Math.round(sample));
    }

    private boolean shouldHumanize() {
        return Baritone.settings().antiCheatCompatibility.value && Baritone.settings().antiCheatHumanization.value && ctx.player() != null;
    }
}
