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

package baritone.api.utils;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * High level utilities for interacting with held items in a way that respects vanilla's interaction flow.
 */
public interface IItemUseHelper {

    /**
     * Attempts to use the held item on the specified block hit result.
     *
     * @param hand       The hand to use.
     * @param hitResult  The block that should be targeted.
     * @return {@code true} if the interaction consumed an action.
     */
    boolean useItemOn(InteractionHand hand, BlockHitResult hitResult);

    /**
     * Attempts to use the held item without a targeted block (right click in air).
     *
     * @param hand The hand to use.
     * @return {@code true} if the interaction consumed an action.
     */
    boolean useItem(InteractionHand hand);

    /**
     * Requests that the current use action is released. This mirrors the vanilla client behaviour
     * when the player releases the corresponding key.
     */
    void releaseUsingItem();

    /**
     * @return {@code true} if the player is currently using an item.
     */
    boolean isUsingItem();

    /**
     * @return The hand that is currently being used, if any.
     */
    Optional<InteractionHand> activeHand();
}
