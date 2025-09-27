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
import java.util.stream.Stream;

public final class SeedPredictionCommand extends Command {

    public SeedPredictionCommand(IBaritone baritone) {
        super(baritone, "seedbasedprediction");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ISeedPathing seedPathing = baritone.getSeedPathing();
        if (!args.hasAny()) {
            logDirect("Seed-based prediction is " + (seedPathing.isPredictionEnabled() ? "enabled" : "disabled") + ".");
            if (!seedPathing.hasSeed()) {
                logDirect("No predictive seed is configured. Use #seed set <seed> first.");
            }
            return;
        }
        String desired = args.getString().toLowerCase(Locale.US);
        boolean enable;
        if (desired.equals("true") || desired.equals("on") || desired.equals("enable")) {
            enable = true;
        } else if (desired.equals("false") || desired.equals("off") || desired.equals("disable")) {
            enable = false;
        } else {
            throw new CommandInvalidTypeException(args.consumed(), "true/false", desired);
        }
        args.requireMax(0);
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

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle seed-based predictive planning.";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls whether predictive planning uses the configured seed.",
                "",
                "Usage:",
                "> seedbasedprediction - show current status",
                "> seedbasedprediction true|on - enable predictive planning (requires a configured seed)",
                "> seedbasedprediction false|off - disable predictive planning"
        );
    }
}
