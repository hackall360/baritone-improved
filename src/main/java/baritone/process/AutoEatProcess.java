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

package baritone.process;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.process.IAutoEatProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.IItemUseHelper;
import baritone.api.utils.input.Input;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public final class AutoEatProcess extends BaritoneProcessHelper implements IAutoEatProcess {

    private static final int MAX_FOOD_LEVEL = 20;

    private boolean refilling;
    private boolean manualTrigger;
    private boolean announcedNoFood;
    private int selectedSlot = -1;

    public AutoEatProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public boolean isActive() {
        Player player = ctx.player();
        if (player == null || ctx.world() == null) {
            return false;
        }
        if (player.isCreative()) {
            return false;
        }
        if (refilling || manualTrigger) {
            return true;
        }
        if (!Baritone.settings().autoEat.value) {
            return false;
        }
        return player.getFoodData().getFoodLevel() <= Baritone.settings().autoEatThreshold.value && hasEdibleInHotbar(player);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        Player player = ctx.player();
        if (player == null || ctx.world() == null || player.isCreative()) {
            stopEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        final Settings settings = Baritone.settings();
        final int hunger = player.getFoodData().getFoodLevel();
        final boolean thresholdReached = settings.autoEat.value && hunger <= settings.autoEatThreshold.value;

        if (!refilling && (manualTrigger || thresholdReached)) {
            refilling = true;
        }

        if (!refilling) {
            stopEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        if (hunger >= MAX_FOOD_LEVEL && !player.isUsingItem()) {
            stopEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        OptionalInt nextFoodSlot = findFoodSlot(player);
        if (nextFoodSlot.isEmpty() && !player.isUsingItem()) {
            if (!announcedNoFood) {
                logDirect("Auto eat paused - no edible items in hotbar.");
                announcedNoFood = true;
            }
            stopEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        announcedNoFood = false;

        if (!player.isUsingItem()) {
            if (!isSafeToCancel) {
                baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            nextFoodSlot.ifPresent(slot -> {
                if (selectedSlot != slot) {
                    player.getInventory().setSelectedSlot(slot);
                    ctx.playerController().syncHeldItem();
                    selectedSlot = slot;
                }
            });
            baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
            IItemUseHelper itemUseHelper = baritone.getItemUseHelper();
            itemUseHelper.useItem(InteractionHand.MAIN_HAND);
            manualTrigger = false;
        } else {
            manualTrigger = false;
            baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    @Override
    public void onLostControl() {
        stopEating();
    }

    @Override
    public String displayName0() {
        return "auto eat";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 5.6;
    }

    @Override
    public boolean isEating() {
        Player player = ctx.player();
        return refilling || (player != null && player.isUsingItem());
    }

    @Override
    public void requestImmediateEat() {
        manualTrigger = true;
    }

    private OptionalInt findFoodSlot(Player player) {
        NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
        for (int i = 0; i < Math.min(items.size(), 9); i++) {
            ItemStack stack = items.get(i);
            if (isEdible(stack)) {
                return OptionalInt.of(i);
            }
        }
        return OptionalInt.empty();
    }

    private boolean hasEdibleInHotbar(Player player) {
        return findFoodSlot(player).isPresent();
    }

    private boolean isEdible(ItemStack stack) {
        return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }

    private void stopEating() {
        refilling = false;
        manualTrigger = false;
        selectedSlot = -1;
        announcedNoFood = false;
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        IItemUseHelper helper = baritone.getItemUseHelper();
        if (helper.isUsingItem()) {
            helper.releaseUsingItem();
        }
    }
}
