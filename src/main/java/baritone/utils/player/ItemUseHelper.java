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

package baritone.utils.player;

import baritone.api.utils.IItemUseHelper;
import baritone.api.utils.IPlayerContext;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * Client side helper that mirrors vanilla item usage rules to keep behaviour consistent across
 * different interaction types.
 */
public final class ItemUseHelper implements IItemUseHelper {

    private final IPlayerContext ctx;

    public ItemUseHelper(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean useItemOn(InteractionHand hand, BlockHitResult hitResult) {
        LocalPlayer player = ctx.player();
        if (player == null || ctx.world() == null || ctx.minecraft().gameMode == null) {
            return false;
        }
        InteractionResult result = ctx.playerController().processRightClickBlock(player, ctx.world(), hand, hitResult);
        if (result.consumesAction()) {
            player.swing(hand);
        }
        return result.consumesAction();
    }

    @Override
    public boolean useItem(InteractionHand hand) {
        LocalPlayer player = ctx.player();
        if (player == null || ctx.world() == null || ctx.minecraft().gameMode == null) {
            return false;
        }
        InteractionResult result = ctx.playerController().processRightClick(player, ctx.world(), hand);
        if (result.consumesAction()) {
            player.swing(hand);
        }
        return result.consumesAction();
    }

    @Override
    public void releaseUsingItem() {
        LocalPlayer player = ctx.player();
        if (player == null) {
            return;
        }
        if (ctx.minecraft().gameMode != null) {
            ctx.minecraft().gameMode.releaseUsingItem(player);
        } else {
            player.releaseUsingItem();
        }
    }

    @Override
    public boolean isUsingItem() {
        LocalPlayer player = ctx.player();
        return player != null && player.isUsingItem();
    }

    @Override
    public Optional<InteractionHand> activeHand() {
        LocalPlayer player = ctx.player();
        if (player == null || !player.isUsingItem()) {
            return Optional.empty();
        }
        return Optional.of(player.getUsedItemHand());
    }
}
