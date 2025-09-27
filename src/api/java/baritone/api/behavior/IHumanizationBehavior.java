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

package baritone.api.behavior;

import baritone.api.behavior.humanization.HumanizationProfileSnapshot;

/**
 * Provides access to the adaptive humanization layer. Implementations are responsible for
 * recording player telemetry and producing snapshots of the accumulated profile so that other
 * systems can synthesize "human-like" behavior.
 */
public interface IHumanizationBehavior extends IBehavior {

    /**
     * Starts recording the player's inputs and interactions. Recording sessions accumulate and are
     * persisted across launches until {@link #resetProfile()} is invoked.
     */
    void startRecording();

    /**
     * Stops recording the player's inputs. Any captured samples are flushed to disk.
     */
    void stopRecording();

    /**
     * Clears all recorded samples and stored state.
     */
    void resetProfile();

    /**
     * @return whether a recording session is currently active.
     */
    boolean isRecording();

    /**
     * Returns an immutable snapshot of the accumulated humanization profile.
     *
     * @return the current snapshot.
     */
    HumanizationProfileSnapshot snapshot();
}
