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
import baritone.api.Settings;
import baritone.api.command.Command;
import baritone.api.command.CommandCategory;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.Paginator;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.utils.SettingsUtil;
import baritone.settings.SettingsProfileManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static baritone.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;
import static baritone.api.utils.SettingsUtil.*;

public class SetCommand extends Command {

    public SetCommand(IBaritone baritone) {
        super(baritone, CommandCategory.SETTINGS, "set", "setting", "settings");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        Baritone impl = (Baritone) this.baritone;
        SettingsProfileManager profiles = impl.getSettingsProfileManager();

        boolean globalContext = false;
        String arg = args.hasAny() ? args.getString() : "list";
        if (arg.equalsIgnoreCase("global")) {
            globalContext = true;
            arg = args.hasAny() ? args.getString() : "list";
        }

        String lowerArg = arg.toLowerCase(Locale.US);
        if (Arrays.asList("s", "save").contains(lowerArg)) {
            if (!globalContext && args.hasAny()) {
                String next = args.peekString();
                if ("global".equalsIgnoreCase(next)) {
                    args.getString();
                    globalContext = true;
                }
            }
            if (globalContext) {
                profiles.saveGlobalOverrides();
                logDirect("Global settings saved");
            } else {
                profiles.saveActiveProfile();
                logDirect(String.format("Settings saved for %s", profiles.describeActiveProfile()));
            }
            return;
        }
        if (Arrays.asList("load", "ld").contains(lowerArg)) {
            String file = globalContext ? SETTINGS_DEFAULT_NAME : profiles.getActiveProfileFile();
            if (args.hasAny()) {
                file = args.getString();
            }
            if (globalContext) {
                profiles.loadOverridesFromFile(file, true);
            } else {
                profiles.loadOverridesFromFile(file, false);
            }
            logDirect(String.format("Settings reloaded from %s for %s", file, profiles.describeScope(globalContext)));
            return;
        }
        boolean viewModified = Arrays.asList("m", "mod", "modified").contains(arg);
        boolean viewAll = Arrays.asList("all", "l", "list").contains(arg);
        boolean paginate = viewModified || viewAll;
        if (paginate) {
            String search = args.hasAny() && args.peekAsOrNull(Integer.class) == null ? args.getString() : "";
            args.requireMax(1);
            List<? extends Settings.Setting> toPaginate =
                    (viewModified ? SettingsUtil.modifiedSettings(Baritone.settings()) : Baritone.settings().allSettings).stream()
                            .filter(s -> !s.isJavaOnly())
                            .filter(s -> s.getName().toLowerCase(Locale.US).contains(search.toLowerCase(Locale.US)))
                            .sorted((s1, s2) -> String.CASE_INSENSITIVE_ORDER.compare(s1.getName(), s2.getName()))
                            .collect(Collectors.toList());
            Paginator.paginate(
                    args,
                    new Paginator<>(toPaginate),
                    () -> logDirect(
                            !search.isEmpty()
                                    ? String.format("All %ssettings containing the string '%s':", viewModified ? "modified " : "", search)
                                    : String.format("All %ssettings:", viewModified ? "modified " : "")
                    ),
                    setting -> {
                        MutableComponent typeComponent = Component.literal(String.format(
                                " (%s)",
                                settingTypeToString(setting)
                        ));
                        typeComponent.setStyle(typeComponent.getStyle().withColor(ChatFormatting.DARK_GRAY));
                        MutableComponent hoverComponent = Component.literal("");
                        hoverComponent.setStyle(hoverComponent.getStyle().withColor(ChatFormatting.GRAY));
                        hoverComponent.append(setting.getName());
                        hoverComponent.append(String.format("\nType: %s", settingTypeToString(setting)));
                        hoverComponent.append(String.format("\n\nValue:\n%s", settingValueToString(setting)));
                        hoverComponent.append(String.format("\n\nDefault Value:\n%s", settingDefaultToString(setting)));
                        String commandSuggestion = Baritone.settings().prefix.value + String.format("set %s ", setting.getName());
                        MutableComponent component = Component.literal(setting.getName());
                        component.setStyle(component.getStyle().withColor(ChatFormatting.GRAY));
                        component.append(typeComponent);
                        component.setStyle(component.getStyle()
                                .withHoverEvent(new HoverEvent.ShowText(hoverComponent))
                                .withClickEvent(new ClickEvent.SuggestCommand(commandSuggestion)));
                        return component;
                    },
                    FORCE_COMMAND_PREFIX + "set " + arg + " " + search
            );
            return;
        }
        args.requireMax(1);
        boolean resetting = lowerArg.equals("reset");
        boolean toggling = lowerArg.equals("toggle");
        boolean doingSomething = resetting || toggling;
        if (resetting) {
            if (!args.hasAny()) {
                logDirect("Please specify 'all' as an argument to reset to confirm you'd really like to do this");
                logDirect("ALL settings will be reset. Use the 'set modified' or 'modified' commands to see what will be reset.");
                logDirect("Specify a setting name instead of 'all' to only reset one setting");
            } else if (args.peekString().equalsIgnoreCase("all")) {
                args.getString();
                profiles.resetAll(globalContext);
                logDirect(String.format("All settings for %s have been reset to their default values", profiles.describeScope(globalContext)));
                profiles.saveScope(globalContext);
                return;
            }
        }
        if (toggling) {
            args.requireMin(1);
        }
        String settingName = doingSomething ? args.getString() : arg;
        Settings.Setting<?> setting = Baritone.settings().allSettings.stream()
                .filter(s -> s.getName().equalsIgnoreCase(settingName))
                .findFirst()
                .orElse(null);
        if (setting == null) {
            throw new CommandInvalidTypeException(args.consumed(), "a valid setting");
        }
        if (setting.isJavaOnly()) {
            // ideally it would act as if the setting didn't exist
            // but users will see it in Settings.java or its javadoc
            // so at some point we have to tell them or they will see it as a bug
            throw new CommandInvalidStateException(String.format("Setting %s can only be used via the api.", setting.getName()));
        }
        if (!doingSomething && !args.hasAny()) {
            logDirect(String.format("Value of setting %s:", setting.getName()));
            logDirect(settingValueToString(setting));
        } else {
            String oldValue = settingValueToString(setting);
            if (resetting) {
                profiles.resetSetting(setting, globalContext);
            } else if (toggling) {
                if (setting.getValueClass() != Boolean.class) {
                    throw new CommandInvalidTypeException(args.consumed(), "a toggleable setting", "some other setting");
                }
                //noinspection unchecked
                Settings.Setting<Boolean> asBoolSetting = (Settings.Setting<Boolean>) setting;
                boolean newValue = !asBoolSetting.value;
                if (newValue && setting == Baritone.settings().ignoreServerOreData
                        && !impl.getSeedPathing().hasSeed()) {
                    throw new CommandInvalidStateException("Cannot enable ignoreServerOreData without a configured seed");
                }
                profiles.setValue(setting, Boolean.toString(newValue), globalContext);
                logDirect(String.format(
                        "Toggled setting %s to %s for %s",
                        setting.getName(),
                        Boolean.toString((Boolean) setting.value),
                        profiles.describeScope(globalContext)
                ));
            } else {
                String newValue = args.getString();
                try {
                    profiles.setValue(setting, newValue, globalContext);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw new CommandInvalidTypeException(args.consumed(), "a valid value", t);
                }
            }
            if (!toggling) {
                logDirect(String.format(
                        "Successfully %s %s to %s for %s",
                        resetting ? "reset" : "set",
                        setting.getName(),
                        settingValueToString(setting),
                        profiles.describeScope(globalContext)
                ));
            }
            MutableComponent oldValueComponent = Component.literal(String.format("Old value: %s", oldValue));
            oldValueComponent.setStyle(oldValueComponent.getStyle()
                    .withColor(ChatFormatting.GRAY)
                    .withHoverEvent(new HoverEvent.ShowText(
                            Component.literal("Click to set the setting back to this value")
                    ))
                    .withClickEvent(new ClickEvent.RunCommand(
                            FORCE_COMMAND_PREFIX + String.format(
                                    globalContext ? "set global %s %s" : "set %s %s",
                                    setting.getName(),
                                    oldValue
                            )
                    )));
            logDirect(oldValueComponent);
            if ((setting.getName().equals("chatControl") && !(Boolean) setting.value && !Baritone.settings().chatControlAnyway.value) ||
                    setting.getName().equals("chatControlAnyway") && !(Boolean) setting.value && !Baritone.settings().chatControl.value) {
                logDirect("Warning: Chat commands will no longer work. If you want to revert this change, use prefix control (if enabled) or click the old value listed above.", ChatFormatting.RED);
            } else if (setting.getName().equals("prefixControl") && !(Boolean) setting.value) {
                logDirect("Warning: Prefixed commands will no longer work. If you want to revert this change, use chat control (if enabled) or click the old value listed above.", ChatFormatting.RED);
            }
        }
        profiles.saveScope(globalContext);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .addSettings()
                    .sortAlphabetically()
                    .prepend("list", "modified", "reset", "toggle", "save", "load", "global")
                    .stream();
        }

        String first = args.getString();
        if (first.equalsIgnoreCase("global")) {
            if (!args.hasAny()) {
                return tabCompleteAfterPrefix(args, "", false);
            }
            return tabCompleteAfterPrefix(args, args.getString(), false);
        }

        return tabCompleteAfterPrefix(args, first, true);
    }

    private Stream<String> tabCompleteAfterPrefix(IArgConsumer args, String currentArg, boolean includeGlobal) throws CommandException {
        if (args.hasExactlyOne() && !Arrays.asList("s", "save").contains(args.peekString().toLowerCase(Locale.US))) {
            if (currentArg.equalsIgnoreCase("reset")) {
                return new TabCompleteHelper()
                        .addModifiedSettings()
                        .prepend("all")
                        .filterPrefix(args.getString())
                        .stream();
            } else if (currentArg.equalsIgnoreCase("toggle")) {
                return new TabCompleteHelper()
                        .addToggleableSettings()
                        .filterPrefix(args.getString())
                        .stream();
            } else if (Arrays.asList("ld", "load").contains(currentArg.toLowerCase(Locale.US))) {
                return RelativeFile.tabComplete(args, Minecraft.getInstance().gameDirectory.toPath().resolve("baritone").toFile());
            }
            Settings.Setting setting = Baritone.settings().byLowerName.get(currentArg.toLowerCase(Locale.US));
            if (setting != null) {
                if (setting.getType() == Boolean.class) {
                    TabCompleteHelper helper = new TabCompleteHelper();
                    if ((Boolean) setting.value) {
                        helper.append("true", "false");
                    } else {
                        helper.append("false", "true");
                    }
                    return helper.filterPrefix(args.getString()).stream();
                } else {
                    return Stream.of(settingValueToString(setting));
                }
            }
        } else if (!args.hasAny()) {
            TabCompleteHelper helper = new TabCompleteHelper()
                    .addSettings()
                    .sortAlphabetically()
                    .prepend("list", "modified", "reset", "toggle", "save", "load");
            if (includeGlobal) {
                helper.prepend("global");
            }
            return helper.filterPrefix(currentArg).stream();
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "View or change settings";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Using the set command, you can manage all of Baritone's settings. Almost every aspect is controlled by these settings - go wild!",
                "",
                "Usage:",
                "> set - Same as `set list`",
                "> set list [page] - View all settings",
                "> set modified [page] - View modified settings",
                "> set <setting> - View the current value of a setting",
                "> set <setting> <value> - Set the value of a setting for the current server or world",
                "> set global <setting> <value> - Set a global default value that applies everywhere",
                "> set reset all - Reset all settings for the current server or world to their defaults",
                "> set global reset all - Reset ALL global settings to their defaults",
                "> set reset <setting> - Reset a setting to its default",
                "> set toggle <setting> - Toggle a boolean setting",
                "> set save - Save settings for the current server or world (done automatically)",
                "> set save global - Save global settings",
                "> set load - Reload settings for the current server or world",
                "> set load [filename] - Load settings from another file into the active profile",
                "> set global load [filename] - Load settings from another file into the global profile"
        );
    }
}
