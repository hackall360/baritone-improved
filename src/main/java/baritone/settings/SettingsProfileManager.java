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

package baritone.settings;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Helper;
import baritone.api.utils.SettingsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Manages the currently active settings profile based on the connected server or singleplayer world.
 * Global overrides are loaded first and per-server or per-world overrides are layered on top.
 */
public class SettingsProfileManager implements AbstractGameEventListener {

    private static final Pattern INVALID_CHARACTERS = Pattern.compile("[^a-zA-Z0-9._-]");

    private final Baritone baritone;
    private final Settings settings;

    private final Map<String, String> globalOverrides = new HashMap<>();
    private final Map<String, String> profileOverrides = new HashMap<>();

    private String activeProfileFile = SettingsUtil.SETTINGS_DEFAULT_NAME;
    private String activeProfileDescription = "global profile";

    public SettingsProfileManager(Baritone baritone) {
        this.baritone = baritone;
        this.settings = Baritone.settings();
        reloadGlobalOverrides();
        refreshProfileFromEnvironment();
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        if (event.getState() != EventState.POST) {
            return;
        }
        refreshProfileFromEnvironment();
    }

    public void refreshProfileFromEnvironment() {
        ProfileTarget target = computeProfileTarget();
        boolean changedFile = !Objects.equals(target.file, this.activeProfileFile);
        boolean changedDescription = !Objects.equals(target.description, this.activeProfileDescription);

        if (changedFile) {
            this.activeProfileFile = target.file;
            this.profileOverrides.clear();
            if (!SettingsUtil.SETTINGS_DEFAULT_NAME.equals(this.activeProfileFile)) {
                this.profileOverrides.putAll(SettingsUtil.readRaw(this.activeProfileFile, false));
            }
            Helper.HELPER.logDirect("Loaded Baritone settings for " + target.description);
        }

        this.activeProfileDescription = target.description;

        if (changedFile || changedDescription) {
            refreshSettings();
        }
    }

    public void reloadGlobalOverrides() {
        this.globalOverrides.clear();
        this.globalOverrides.putAll(SettingsUtil.readRaw(SettingsUtil.SETTINGS_DEFAULT_NAME));
        refreshSettings();
    }

    public void reloadActiveProfile() {
        this.profileOverrides.clear();
        if (!SettingsUtil.SETTINGS_DEFAULT_NAME.equals(this.activeProfileFile)) {
            this.profileOverrides.putAll(SettingsUtil.readRaw(this.activeProfileFile, false));
        }
        refreshSettings();
    }

    public void loadOverridesFromFile(String file, boolean global) {
        Map<String, String> data = SettingsUtil.readRaw(file, global);
        if (global) {
            this.globalOverrides.clear();
            this.globalOverrides.putAll(data);
        } else {
            this.profileOverrides.clear();
            this.profileOverrides.putAll(data);
        }
        refreshSettings();
    }

    public void resetAll(boolean global) {
        if (global) {
            this.globalOverrides.clear();
        } else {
            this.profileOverrides.clear();
        }
        refreshSettings();
    }

    public void resetSetting(Settings.Setting<?> setting, boolean global) {
        Map<String, String> target = global ? this.globalOverrides : this.profileOverrides;
        target.remove(setting.getName().toLowerCase(Locale.US));
        refreshSettings();
    }

    public void setValue(Settings.Setting<?> setting, String rawValue, boolean global) {
        SettingsUtil.parseAndApply(this.settings, setting.getName().toLowerCase(Locale.US), rawValue);
        Map<String, String> target = global ? this.globalOverrides : this.profileOverrides;
        target.put(setting.getName().toLowerCase(Locale.US), SettingsUtil.settingValueToString(setting));
        refreshSettings();
    }

    public void saveScope(boolean global) {
        if (global) {
            saveGlobalOverrides();
        } else {
            saveActiveProfile();
        }
    }

    public void saveGlobalOverrides() {
        Map<String, String> relevant = filteredOverrides(this.globalOverrides, true);
        SettingsUtil.writeRaw(SettingsUtil.SETTINGS_DEFAULT_NAME, relevant);
    }

    public void saveActiveProfile() {
        if (SettingsUtil.SETTINGS_DEFAULT_NAME.equals(this.activeProfileFile)) {
            saveGlobalOverrides();
            return;
        }
        Map<String, String> relevant = filteredOverrides(this.profileOverrides, false);
        SettingsUtil.writeRaw(this.activeProfileFile, relevant);
    }

    public String describeActiveProfile() {
        return this.activeProfileDescription;
    }

    public String describeScope(boolean global) {
        return global ? "the global profile" : describeActiveProfile();
    }

    public String getActiveProfileFile() {
        return this.activeProfileFile;
    }

    private void refreshSettings() {
        SettingsUtil.modifiedSettings(this.settings).forEach(Settings.Setting::reset);
        applyOverrides(this.globalOverrides);
        applyOverrides(this.profileOverrides);
    }

    private void applyOverrides(Map<String, String> overrides) {
        overrides.forEach((settingName, value) -> {
            try {
                SettingsUtil.parseAndApply(this.settings, settingName, value);
            } catch (Exception ex) {
                Helper.HELPER.logDirect("Failed to apply setting " + settingName + " from profile");
                ex.printStackTrace();
            }
        });
    }

    private Map<String, String> filteredOverrides(Map<String, String> overrides, boolean global) {
        Map<String, String> filtered = new HashMap<>();
        overrides.forEach((name, value) -> {
            Settings.Setting<?> setting = this.settings.byLowerName.get(name);
            if (setting == null) {
                return;
            }
            String baseline = global
                    ? SettingsUtil.settingDefaultToString(setting)
                    : this.globalOverrides.getOrDefault(name, SettingsUtil.settingDefaultToString(setting));
            if (!Objects.equals(value, baseline)) {
                filtered.put(name, value);
            }
        });
        return filtered;
    }

    private ProfileTarget computeProfileTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.hasSingleplayerServer()) {
            MinecraftServer server = minecraft.getSingleplayerServer();
            if (server != null) {
                Path worldPath = server.getWorldPath(LevelResource.ROOT).normalize();
                String folderName = worldPath.getFileName() != null ? worldPath.getFileName().toString() : "singleplayer";
                Path savesDir = minecraft.gameDirectory.toPath().resolve("saves");
                if (worldPath.startsWith(savesDir) && savesDir.relativize(worldPath).getNameCount() > 0) {
                    folderName = savesDir.relativize(worldPath).getName(0).toString();
                }
                String sanitized = sanitizeIdentifier(folderName);
                if (sanitized.isEmpty()) {
                    sanitized = "singleplayer";
                }
                String file = "settings/saves/" + sanitized + ".txt";
                return new ProfileTarget(file, "world " + folderName);
            }
        }

        if (minecraft != null) {
            ServerData server = minecraft.getCurrentServer();
            if (server != null) {
                String identifier = server.ip == null || server.ip.isBlank() ? server.name : server.ip;
                if (identifier == null || identifier.isBlank()) {
                    identifier = "server";
                }
                if (server.isRealm()) {
                    identifier = "realms_" + identifier;
                }
                String sanitized = sanitizeIdentifier(identifier);
                if (sanitized.isEmpty()) {
                    sanitized = "server";
                }
                String file = "settings/servers/" + sanitized + ".txt";
                String description = server.name != null && !server.name.isBlank() ? server.name : identifier;
                return new ProfileTarget(file, "server " + description);
            }
        }

        return new ProfileTarget(SettingsUtil.SETTINGS_DEFAULT_NAME, "global profile");
    }

    private String sanitizeIdentifier(String raw) {
        return INVALID_CHARACTERS.matcher(raw).replaceAll("_");
    }

    private static final class ProfileTarget {
        final String file;
        final String description;

        private ProfileTarget(String file, String description) {
            this.file = file;
            this.description = description;
        }
    }
}
