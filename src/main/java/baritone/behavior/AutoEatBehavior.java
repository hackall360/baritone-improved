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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import net.minecraft.world.entity.player.Player;

/**
 * Coordinates automatic eating with the shared Baritone state so that
 * manual triggers and combat safety checks can coexist with pathing.
 */
public final class AutoEatBehavior extends Behavior {

    public AutoEatBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }

        Player player = ctx.player();
        if (player == null || ctx.world() == null || player.isCreative()) {
            return;
        }

        if (!Baritone.settings().autoEat.value) {
            return;
        }

        boolean hungerLow = player.getFoodData().getFoodLevel() <= Baritone.settings().autoEatThreshold.value;
        boolean healthLow = player.getHealth() <= Baritone.settings().combatEatHpThreshold.value;

        if ((hungerLow || healthLow) && !baritone.getAutoEatProcess().isEating()) {
            baritone.getAutoEatProcess().requestImmediateEat();
        }
    }
}
