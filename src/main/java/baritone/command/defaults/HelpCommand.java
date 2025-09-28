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
import baritone.api.command.CommandDomain;
import baritone.api.command.ICommand;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandNotFoundException;
import baritone.api.command.helpers.TabCompleteHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.*;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class HelpCommand extends Command {

    public HelpCommand(IBaritone baritone) {
        super(baritone, "help", "?");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(1);
        if (!args.hasAny()) {
            renderGroupedOverview(label);
            return;
        }
        if (args.is(Integer.class)) {
            args.getAs(Integer.class); // Consume but ignore legacy page numbers
            renderGroupedOverview(label);
            return;
        }
        {
            String commandName = args.getString().toLowerCase();
            ICommand command = this.baritone.getCommandManager().getCommand(commandName);
            if (command == null) {
                throw new CommandNotFoundException(commandName);
            }
            logDirect(String.format("%s - %s", String.join(" / ", command.getNames()), command.getShortDesc()));
            logDirect("");
            command.getLongDesc().forEach(this::logDirect);
            logDirect("");
            MutableComponent returnComponent = Component.literal("Click to return to the help menu");
            returnComponent.setStyle(returnComponent.getStyle().withClickEvent(new ClickEvent.RunCommand(
                    FORCE_COMMAND_PREFIX + label
            )));
            logDirect(returnComponent);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper()
                    .addCommands(this.baritone.getCommandManager())
                    .filterPrefix(args.getString())
                    .stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View all commands or help on specific ones";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using this command, you can view detailed help information on how to use certain commands of Baritone.",
                "",
                "Usage:",
                "> help - Lists all commands grouped by domain and category.",
                "> help <command> - Displays help information on a specific command."
        );
    }

    private void renderGroupedOverview(String label) {
        Map<CommandDomain, Map<CommandCategory, List<ICommand>>> grouped = new EnumMap<>(CommandDomain.class);
        this.baritone.getCommandManager().getRegistry().descendingStream()
                .filter(command -> !command.hiddenFromHelp())
                .forEach(command -> grouped
                        .computeIfAbsent(command.getDomain(), domain -> new EnumMap<>(CommandCategory.class))
                        .computeIfAbsent(command.getCategory(), category -> new ArrayList<>())
                        .add(command));

        logDirect(Component.literal("All Baritone commands (clickable) grouped by domain:")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD)));

        boolean firstDomain = true;
        for (CommandDomain domain : CommandDomain.values()) {
            Map<CommandCategory, List<ICommand>> categories = grouped.get(domain);
            if (categories == null || categories.isEmpty()) {
                continue;
            }
            if (!firstDomain) {
                logDirect(Component.literal(""));
            }
            firstDomain = false;
            MutableComponent domainComponent = Component.literal(domain.displayName());
            domainComponent.setStyle(domainComponent.getStyle().withColor(ChatFormatting.YELLOW).withBold(true));
            logDirect(domainComponent);

            for (CommandCategory category : CommandCategory.values()) {
                if (category.domain() != domain) {
                    continue;
                }
                List<ICommand> commands = categories.get(category);
                if (commands == null || commands.isEmpty()) {
                    continue;
                }
                commands.sort(Comparator.comparing(cmd -> cmd.getNames().get(0)));
                MutableComponent categoryComponent = Component.literal("  " + category.displayName());
                categoryComponent.setStyle(categoryComponent.getStyle().withColor(ChatFormatting.AQUA));
                logDirect(categoryComponent);
                for (ICommand command : commands) {
                    MutableComponent entry = Component.literal("    ");
                    entry.append(createCommandComponent(label, command));
                    logDirect(entry);
                }
            }
        }
    }

    private MutableComponent createCommandComponent(String label, ICommand command) {
        String names = String.join("/", command.getNames());
        String name = command.getNames().get(0);
        MutableComponent shortDescComponent = Component.literal(" - " + command.getShortDesc());
        shortDescComponent.setStyle(shortDescComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
        MutableComponent namesComponent = Component.literal(names);
        namesComponent.setStyle(namesComponent.getStyle().withColor(ChatFormatting.WHITE));
        MutableComponent hoverComponent = Component.literal("");
        hoverComponent.setStyle(hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
        hoverComponent.append(namesComponent);
        hoverComponent.append("\n" + command.getShortDesc());
        hoverComponent.append("\n\nClick to view full help");
        String clickCommand = FORCE_COMMAND_PREFIX + String.format("%s %s", label, command.getNames().get(0));
        MutableComponent component = Component.literal(name);
        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
        component.append(shortDescComponent);
        component.setStyle(component.getStyle()
                .withHoverEvent(new HoverEvent.ShowText(hoverComponent))
                .withClickEvent(new ClickEvent.RunCommand(clickCommand)));
        return component;
    }
}
