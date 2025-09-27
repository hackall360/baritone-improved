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

package baritone.api.pathing.seed;

import java.util.OptionalLong;

/**
 * Public API for controlling seed-based predictive pathing.
 */
public interface ISeedPathing {

    /**
     * @return the configured seed if one has been set.
     */
    OptionalLong seed();

    /**
     * @return {@code true} if a seed has been configured for predictive planning.
     */
    boolean hasSeed();

    /**
     * Configure the seed that should be used for predictive planning.
     *
     * @param seed the world seed to use
     */
    void setSeed(long seed);

    /**
     * Clear the configured seed, disabling predictive planning until a new seed is provided.
     */
    void clearSeed();

    /**
     * @return {@code true} if predictive planning is currently active and will be applied to path calculations.
     */
    boolean isPredictionEnabled();

    /**
     * Enable or disable predictive planning. Implementations may return {@code false} when enabling without a configured seed.
     *
     * @param enabled whether predictive planning should be active
     * @return {@code true} if predictive planning will be active after applying the requested state
     */
    boolean setPredictionEnabled(boolean enabled);
}
