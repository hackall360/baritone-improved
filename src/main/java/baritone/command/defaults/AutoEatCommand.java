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

public class AutoEatCommand extends Command {

    public AutoEatCommand(IBaritone baritone) {
        super(baritone, "autoeat");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireExactly(1);
        boolean enabled = args.getAs(Boolean.class);
        Baritone.settings().autoEat.value = enabled;
        logDirect(String.format(Locale.US, "Auto eat %s.", enabled ? "enabled" : "disabled"));
        if (enabled && ctx.player() != null && ctx.player().getFoodData().getFoodLevel() <= Baritone.settings().autoEatThreshold.value) {
            baritone.getAutoEatProcess().requestImmediateEat();
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString().toLowerCase(Locale.US);
            return Stream.of("true", "false").filter(option -> option.startsWith(prefix));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Toggle automatic hunger refills";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The autoeat command toggles automatic hunger refills.",
                "",
                "Usage:",
                "> autoeat <true|false> - Enables or disables automatic eating."
        );
    }
}
