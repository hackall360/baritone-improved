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
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.pathing.calc.IPath;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.Pair;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Monitors block change notifications so that any mutations along the
 * currently executing path can trigger a safe revalidation.
 */
public final class PathRepairBehavior extends Behavior {

    public PathRepairBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onBlockChange(BlockChangeEvent event) {
        if (!baritone.getPathingBehavior().isPathing()) {
            return;
        }

        IPath currentPath = baritone.getPathingBehavior().getPath().orElse(null);
        if (currentPath == null || currentPath.length() == 0) {
            return;
        }

        Set<Long> changed = new HashSet<>();
        for (Pair<BlockPos, ?> pair : event.getBlocks()) {
            BlockPos pos = pair.first();
            changed.add(BetterBlockPos.longHash(pos.getX(), pos.getY(), pos.getZ()));
        }

        boolean intersects = currentPath.positions().stream()
                .map(BetterBlockPos::longHash)
                .anyMatch(changed::contains);

        if (intersects) {
            baritone.getPathingBehavior().softCancelIfSafe();
        }
    }
}
