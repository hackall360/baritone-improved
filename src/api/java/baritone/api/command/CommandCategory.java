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

package baritone.api.command;

/**
 * Categories offer a finer grained grouping inside a {@link CommandDomain}. They are used exclusively
 * for presentation and organisation purposes inside the help command.
 */
public enum CommandCategory {

    GENERAL(CommandDomain.DIRECT, "General"),
    CONTROL(CommandDomain.DIRECT, "Execution control"),
    NAVIGATION(CommandDomain.DIRECT, "Navigation & goals"),
    TASKS(CommandDomain.DIRECT, "Automation tasks"),
    BUILDING(CommandDomain.DIRECT, "Building & schematics"),
    WAYPOINTS(CommandDomain.DIRECT, "Waypoints & regions"),
    RENDERING(CommandDomain.DIRECT, "Rendering & visuals"),
    UTILITY(CommandDomain.DIRECT, "Utilities"),
    DIAGNOSTICS(CommandDomain.DIRECT, "Diagnostics & debugging"),
    SETTINGS(CommandDomain.SETTINGS, "Configuration"),
    AUTOMATION_SETTINGS(CommandDomain.SETTINGS, "Automation settings"),
    SEED(CommandDomain.SETTINGS, "Seed & prediction"),
    HUMANIZATION(CommandDomain.SETTINGS, "Humanization & behaviour");

    private final CommandDomain domain;
    private final String displayName;

    CommandCategory(CommandDomain domain, String displayName) {
        this.domain = domain;
        this.displayName = displayName;
    }

    public CommandDomain domain() {
        return domain;
    }

    public String displayName() {
        return displayName;
    }
}
