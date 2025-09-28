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
import baritone.api.behavior.IHumanizationBehavior;
import baritone.api.behavior.humanization.HumanizationProfileSnapshot;
import baritone.api.command.Command;
import baritone.api.command.CommandCategory;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidTypeException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Provides control over the adaptive humanization recorder.
 */
public class HumanizationCommand extends Command {

    private static final List<String> ACTIONS = Arrays.asList("record", "stop", "reset", "status");

    public HumanizationCommand(IBaritone baritone) {
        super(baritone, CommandCategory.HUMANIZATION, "humanization");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        final IHumanizationBehavior behavior = this.baritone.getHumanizationBehavior();

        if (!args.hasAny()) {
            emitStatus(behavior);
            return;
        }

        final ICommandArgument raw = args.get();
        final String subCommand = raw.getValue().toLowerCase(Locale.ROOT);

        switch (subCommand) {
            case "record" -> {
                behavior.startRecording();
                logDirect("Humanization recording started. Play as you normally would to teach Baritone.");
            }
            case "stop" -> {
                behavior.stopRecording();
                logDirect(String.format(Locale.ROOT,
                        "Recording stopped. Profile now includes %d ticks of input.",
                        behavior.snapshot().getRecordedTicks()));
            }
            case "reset" -> {
                behavior.resetProfile();
                logDirect("Humanization profile cleared.");
            }
            case "status" -> emitStatus(behavior);
            default -> throw new CommandInvalidTypeException(raw, "record / stop / reset / status", subCommand);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasAny()) {
            final String prefix = args.peekString().toLowerCase(Locale.ROOT);
            return ACTIONS.stream().filter(action -> action.startsWith(prefix));
        }
        return ACTIONS.stream();
    }

    @Override
    public String getShortDesc() {
        return "Control the adaptive humanization recorder.";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "#humanization record - begin recording your own inputs and block interactions.",
                "#humanization stop - stop recording while keeping any collected samples.",
                "#humanization reset - delete all recorded samples.",
                "#humanization status - display recording state and sample counts."
        );
    }

    private void emitStatus(IHumanizationBehavior behavior) {
        final HumanizationProfileSnapshot snapshot = behavior.snapshot();
        logDirect(String.format(Locale.ROOT, "Recording: %s", behavior.isRecording() ? "active" : "inactive"));
        logDirect(String.format(Locale.ROOT,
                "Ticks: %d | Movement samples: %d | Rotation samples: %d | Block breaks: %d | Block uses: %d",
                snapshot.getRecordedTicks(),
                snapshot.getMovementMagnitude().getCount(),
                snapshot.getYawDelta().getCount(),
                snapshot.getBlockBreakDuration().getCount(),
                snapshot.getBlockPlaceInterval().getCount()));
    }
}
