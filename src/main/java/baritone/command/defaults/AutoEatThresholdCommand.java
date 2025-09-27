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

package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class AutoEatThresholdCommand extends Command {

    private static final int MAX_FOOD_LEVEL = 20;

    public AutoEatThresholdCommand(IBaritone baritone) {
        super(baritone, "autoeatthreshold");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        int requested = args.getAs(Integer.class);
        int clamped = Math.max(0, Math.min(MAX_FOOD_LEVEL, requested));
        Baritone.settings().autoEatThreshold.value = clamped;
        logDirect(String.format(Locale.US, "Auto eat threshold set to %d.", clamped));
        if (clamped != requested) {
            logDirect(String.format(Locale.US, "Value clamped to the valid hunger range of 0-%d.", MAX_FOOD_LEVEL));
        }
        if (Baritone.settings().autoEat.value && ctx.player() != null && ctx.player().getFoodData().getFoodLevel() <= clamped) {
            baritone.getAutoEatProcess().requestImmediateEat();
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Configure the hunger threshold for automatic eating";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The autoeatthreshold command sets the hunger level where automatic eating starts.",
                "",
                "Usage:",
                "> autoeatthreshold <level> - Sets the trigger level (0-20)."
        );
    }
}
