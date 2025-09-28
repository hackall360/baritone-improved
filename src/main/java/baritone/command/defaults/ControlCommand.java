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
import baritone.api.behavior.IPathingBehavior;
import baritone.api.command.Command;
import baritone.api.command.CommandCategory;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.exception.CommandInvalidTypeException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.api.pathing.calc.IPathingControlManager;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Central command for controlling execution: pause/resume/status/cancel/etc.
 * Consolidates the legacy pause, cancel, forcecancel and kill commands.
 */
public final class ControlCommand extends Command {

    private static final List<String> PRIMARY_ACTIONS = Arrays.asList("pause", "resume", "status", "cancel");
    private static final Set<String> STATUS_LABELS = Set.of("status", "paused");
    private static final Set<String> CANCEL_LABELS = Set.of("cancel", "stop", "halt", "forcecancel", "kill");

    private boolean paused;

    public ControlCommand(IBaritone baritone) {
        super(baritone, CommandCategory.CONTROL,
                "control", "pause", "resume", "status", "paused", "cancel", "stop", "halt", "kill", "forcecancel");
        registerPauseProcess();
    }

    private void registerPauseProcess() {
        IPathingControlManager manager = baritone.getPathingControlManager();
        manager.registerProcess(new IBaritoneProcess() {
            @Override
            public boolean isActive() {
                return paused;
            }

            @Override
            public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
                baritone.getInputOverrideHandler().clearAllKeys();
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }

            @Override
            public boolean isTemporary() {
                return true;
            }

            @Override
            public void onLostControl() {
            }

            @Override
            public double priority() {
                return DEFAULT_PRIORITY + 1;
            }

            @Override
            public String displayName0() {
                return "Control Command Pause";
            }
        });
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        String actionToken = resolveActionToken(label, args);
        Action action = Action.fromToken(actionToken);
        boolean force = actionToken.equals("forcecancel") || actionToken.equals("kill");
        boolean release = actionToken.equals("kill");

        if (action != Action.CANCEL && args.hasAny()) {
            args.requireMax(0);
        }

        if (action == Action.CANCEL && label.equals("control")) {
            CancelOptions options = consumeCancelOptions(args);
            force |= options.force();
            release |= options.release();
        } else if (action == Action.CANCEL) {
            args.requireMax(0);
        }

        switch (action) {
            case PAUSE -> handlePause();
            case RESUME -> handleResume();
            case STATUS -> handleStatus();
            case CANCEL -> handleCancel(force, release);
        }
    }

    private CancelOptions consumeCancelOptions(IArgConsumer args) throws CommandException {
        boolean force = false;
        boolean release = false;
        while (args.hasAny()) {
            String option = args.getString().toLowerCase(Locale.US);
            switch (option) {
                case "force":
                case "forceful":
                case "hard":
                    force = true;
                    break;
                case "release":
                case "clear":
                case "keys":
                case "inputs":
                case "manual":
                case "unlock":
                    release = true;
                    break;
                default:
                    throw new CommandInvalidTypeException(args.consumed(), "force / release", option);
            }
        }
        return new CancelOptions(force, release);
    }

    private void handlePause() throws CommandException {
        if (paused) {
            throw new CommandInvalidStateException("Already paused");
        }
        paused = true;
        logDirect("Paused");
    }

    private void handleResume() throws CommandException {
        baritone.getBuilderProcess().resume();
        if (!paused) {
            throw new CommandInvalidStateException("Not paused");
        }
        paused = false;
        logDirect("Resumed");
    }

    private void handleStatus() {
        logDirect(String.format("Baritone is %spaused", paused ? "" : "not "));
    }

    private void handleCancel(boolean force, boolean release) {
        if (paused) {
            paused = false;
        }
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        pathingBehavior.cancelEverything();
        if (force) {
            pathingBehavior.forceCancel();
        }
        if (release) {
            baritone.getInputOverrideHandler().clearAllKeys();
        }
        List<String> notes = new ArrayList<>();
        if (force) {
            notes.add("forced");
        }
        if (release) {
            notes.add("inputs released");
        }
        if (notes.isEmpty()) {
            logDirect("Canceled current tasks.");
        } else {
            logDirect(String.format("Canceled current tasks (%s).", String.join(", ", notes)));
        }
    }

    private String resolveActionToken(String label, IArgConsumer args) throws CommandException {
        String normalized = label.toLowerCase(Locale.US);
        if (!normalized.equals("control")) {
            return normalized;
        }
        if (!args.hasAny()) {
            return "status";
        }
        return args.getString().toLowerCase(Locale.US);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!label.equals("control")) {
            return Stream.empty();
        }
        if (!args.hasAny()) {
            return PRIMARY_ACTIONS.stream();
        }
        String first = args.peekString().toLowerCase(Locale.US);
        if (args.hasExactlyOne()) {
            return new TabCompleteHelper().append(PRIMARY_ACTIONS.toArray(new String[0])).filterPrefix(first).stream();
        }
        if (!"cancel".equals(first)) {
            return Stream.empty();
        }
        if (args.hasExactly(2)) {
            String optionPrefix = args.peekString(1).toLowerCase(Locale.US);
            return Stream.of("force", "release").filter(opt -> opt.startsWith(optionPrefix));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Control running Baritone processes.";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Provides pause/resume/status/cancel controls for Baritone.",
                "",
                "Usage:",
                "> control pause - Temporarily pause all activity.",
                "> control resume - Resume after a pause.",
                "> control status - Display whether Baritone is currently paused.",
                "> control cancel [force] [release] - Cancel current activity, optionally forcing cancellation and releasing inputs.",
                "> kill - Shortcut for a forced cancel that also releases inputs."
        );
    }

    private enum Action {
        PAUSE,
        RESUME,
        STATUS,
        CANCEL;

        static Action fromToken(String token) throws CommandException {
            if (token.equals("pause")) {
                return PAUSE;
            }
            if (token.equals("resume")) {
                return RESUME;
            }
            if (STATUS_LABELS.contains(token)) {
                return STATUS;
            }
            if (CANCEL_LABELS.contains(token)) {
                return CANCEL;
            }
            throw new CommandInvalidTypeException(null, "pause / resume / status / cancel", token);
        }
    }

    private record CancelOptions(boolean force, boolean release) {
    }
}
