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
import baritone.api.command.CommandCategory;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.pathing.seed.ISeedPathing;
import baritone.api.pathing.seed.SeedOreMode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.stream.Stream;

public final class SeedCommand extends Command {

    public SeedCommand(IBaritone baritone) {
        super(baritone, CommandCategory.SEED, "seed");
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
            logDirect("Pregen radius: " + seedPathing.pregenRadius() + " chunks");
            logDirect("Ore mode: " + seedPathing.generationMode().name().toLowerCase(Locale.ROOT));
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
            case "radius" -> {
                args.requireMin(1);
                String literal = args.getString();
                int radius;
                try {
                    radius = Integer.parseInt(literal);
                } catch (NumberFormatException ex) {
                    throw new CommandInvalidTypeException(args.consumed(), "a valid radius", literal);
                }
                args.requireMax(0);
                seedPathing.setPregenRadius(radius);
                logDirect("Predictive pregen radius set to " + seedPathing.pregenRadius() + " chunks.");
            }
            case "mode" -> {
                args.requireMin(1);
                String literal = args.getString();
                SeedOreMode mode;
                try {
                    mode = SeedOreMode.fromString(literal);
                } catch (IllegalArgumentException ex) {
                    throw new CommandInvalidTypeException(args.consumed(), "a valid ore mode", literal);
                }
                args.requireMax(0);
                seedPathing.setGenerationMode(mode);
                logDirect("Predictive ore mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
            }
            case "prediction", "pathing" -> {
                args.requireMin(1);
                String literal = args.getString();
                args.requireMax(0);
                boolean enable = parseToggle(literal, args);
                if (enable) {
                    if (!seedPathing.setPredictionEnabled(true)) {
                        logDirect("Cannot enable seed-based prediction without a configured seed. Use #seed set <seed> first.");
                        return;
                    }
                    logDirect("Seed-based predictive planning enabled.");
                } else {
                    seedPathing.setPredictionEnabled(false);
                    logDirect("Seed-based predictive planning disabled.");
                }
            }
            default -> throw new CommandInvalidTypeException(args.consumed(), "a valid seed action", action);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        List<String> subCommands = Arrays.asList("set", "clear", "radius", "mode", "prediction", "pathing");
        if (!args.hasAny()) {
            return subCommands.stream();
        }
        String first = args.peekString().toLowerCase(Locale.US);
        if (args.hasExactlyOne()) {
            return subCommands.stream().filter(option -> option.startsWith(first));
        }
        if (!args.has(2)) {
            return Stream.empty();
        }
        if (first.equals("mode")) {
            String prefix = args.peekString(1).toLowerCase(Locale.US);
            return Arrays.stream(SeedOreMode.values())
                    .map(mode -> mode.name().toLowerCase(Locale.US))
                    .filter(mode -> mode.startsWith(prefix));
        }
        if (first.equals("prediction") || first.equals("pathing")) {
            String prefix = args.peekString(1).toLowerCase(Locale.US);
            return Stream.of("true", "false", "on", "off", "enable", "disable")
                    .filter(option -> option.startsWith(prefix));
        }
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
                "> seed clear - remove the configured predictive seed",
                "> seed radius <chunks> - update the predictive pre-generation radius",
                "> seed mode <terrain|veins|ores> - control ore enrichment while pre-generating",
                "> seed prediction <on|off> - toggle seed-based path prediction"
        );
    }

    private boolean parseToggle(String literal, IArgConsumer args) throws CommandInvalidTypeException {
        String normalized = literal.toLowerCase(Locale.US);
        if (normalized.equals("true") || normalized.equals("on") || normalized.equals("enable")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("off") || normalized.equals("disable")) {
            return false;
        }
        throw new CommandInvalidTypeException(null, "true/false", literal);
    }
}
