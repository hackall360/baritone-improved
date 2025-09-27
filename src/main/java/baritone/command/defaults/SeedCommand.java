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

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.pathing.seed.ISeedPathing;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.stream.Stream;

public final class SeedCommand extends Command {

    public SeedCommand(IBaritone baritone) {
        super(baritone, "seed");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ISeedPathing seedPathing = baritone.getSeedPathing();
        if (!args.hasAny()) {
            OptionalLong value = seedPathing.seed();
            if (value.isPresent()) {
                logDirect("Configured predictive seed: " + value.getAsLong());
            } else {
                logDirect("No predictive seed configured.");
            }
            return;
        }
        String action = args.getString().toLowerCase(Locale.US);
        switch (action) {
            case "set" -> {
                args.requireMin(1);
                String seedLiteral = args.getString();
                long seed;
                try {
                    seed = Long.parseLong(seedLiteral);
                } catch (NumberFormatException ex) {
                    throw new CommandInvalidTypeException(args.consumed(), "a valid long seed", seedLiteral);
                }
                args.requireMax(0);
                seedPathing.setSeed(seed);
                logDirect("Predictive planning seed set to " + seed + ".");
            }
            case "clear" -> {
                args.requireMax(0);
                seedPathing.clearSeed();
                logDirect("Cleared predictive planning seed.");
            }
            default -> throw new CommandInvalidTypeException(args.consumed(), "a valid seed action", action);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Configure the predictive planning seed.";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Sets or clears the seed used by seed-based predictive planning.",
                "",
                "Usage:",
                "> seed - show the currently configured seed",
                "> seed set <seed> - configure the world seed for predictive planning",
                "> seed clear - remove the configured predictive seed"
        );
    }
}
