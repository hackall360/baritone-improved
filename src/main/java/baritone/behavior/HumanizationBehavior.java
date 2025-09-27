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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.behavior.IHumanizationBehavior;
import baritone.api.behavior.humanization.HumanizationProfileSnapshot;
import baritone.api.event.events.BlockChangeEvent;
import baritone.api.event.events.BlockInteractEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.Pair;
import baritone.humanization.HumanizationProfile;
import baritone.utils.PlayerMovementInput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Records player telemetry and exposes adaptive humanization statistics.
 */
public final class HumanizationBehavior extends Behavior implements IHumanizationBehavior {

    private static final int AUTOSAVE_INTERVAL_TICKS = 200;

    private final Gson gson;
    private final Path profileFile;
    private final HumanizationProfile profile;

    private HumanizationProfileSnapshot cachedSnapshot;
    private boolean recording;
    private boolean seededMovement;
    private long tickCounter;
    private long lastBlockBreakCompletionTick = -1L;
    private long lastBlockPlaceTick = -1L;
    private long lastSprintToggleTick = -1L;
    private float lastStrafe;
    private float lastForward;
    private float lastYaw;
    private float lastPitch;
    private boolean lastSprinting;
    private boolean dirty;
    private int autosaveCountdown = AUTOSAVE_INTERVAL_TICKS;
    private final Map<BlockPos, Long> activeBreakStarts = new HashMap<>();

    public HumanizationBehavior(Baritone baritone) {
        super(baritone);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.profileFile = baritone.getDirectory().resolve("humanization_profile.json");
        this.profile = this.loadProfile();
        this.cachedSnapshot = this.profile.snapshot();
    }

    @Override
    public synchronized void startRecording() {
        if (this.recording) {
            return;
        }
        this.recording = true;
        this.seededMovement = false;
        this.tickCounter = 0L;
        this.lastBlockBreakCompletionTick = -1L;
        this.lastBlockPlaceTick = -1L;
        this.lastSprintToggleTick = -1L;
        this.activeBreakStarts.clear();
        this.autosaveCountdown = AUTOSAVE_INTERVAL_TICKS;
        this.seedFromPlayer();
    }

    @Override
    public synchronized void stopRecording() {
        if (!this.recording) {
            return;
        }
        this.recording = false;
        this.seededMovement = false;
        this.activeBreakStarts.clear();
        this.saveProfile();
    }

    @Override
    public synchronized void resetProfile() {
        this.profile.clear();
        this.cachedSnapshot = HumanizationProfileSnapshot.EMPTY;
        this.dirty = true;
        this.saveProfile();
    }

    @Override
    public synchronized boolean isRecording() {
        return this.recording;
    }

    @Override
    public synchronized HumanizationProfileSnapshot snapshot() {
        if (this.cachedSnapshot == null) {
            this.cachedSnapshot = this.profile.snapshot();
        }
        return this.cachedSnapshot;
    }

    @Override
    public synchronized void onTick(TickEvent event) {
        if (!this.recording || event.getType() != TickEvent.Type.IN) {
            return;
        }

        final LocalPlayer player = this.ctx.player();
        if (player == null) {
            this.seededMovement = false;
            return;
        }

        this.tickCounter++;

        if (player.input instanceof PlayerMovementInput) {
            this.seededMovement = false;
            return;
        }

        this.profile.incrementRecordedTicks();

        if (!this.seededMovement) {
            this.seedFromPlayer();
            return;
        }

        final float currentStrafe = player.xxa;
        final float currentForward = player.zza;
        this.profile.addMovementSample(Math.abs(currentStrafe - this.lastStrafe), Math.abs(currentForward - this.lastForward));
        this.lastStrafe = currentStrafe;
        this.lastForward = currentForward;

        final float yaw = player.getYRot();
        final float pitch = player.getXRot();
        final float yawDelta = Mth.wrapDegrees(yaw - this.lastYaw);
        final float pitchDelta = pitch - this.lastPitch;
        this.profile.addRotationSample(Math.abs(yawDelta), Math.abs(pitchDelta));
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.markDirty();

        final boolean sprinting = player.isSprinting();
        if (sprinting != this.lastSprinting) {
            if (this.lastSprintToggleTick >= 0L) {
                this.profile.addSprintReaction((int) Math.min(Integer.MAX_VALUE, this.tickCounter - this.lastSprintToggleTick));
                this.markDirty();
            }
            this.lastSprintToggleTick = this.tickCounter;
            this.lastSprinting = sprinting;
        }

        if (this.dirty && --this.autosaveCountdown <= 0) {
            this.saveProfile();
            this.autosaveCountdown = AUTOSAVE_INTERVAL_TICKS;
        }
    }

    @Override
    public synchronized void onBlockInteract(BlockInteractEvent event) {
        if (!this.recording || !this.isPlayerActivelyControlling()) {
            return;
        }

        switch (event.getType()) {
            case START_BREAK -> {
                long now = this.tickCounter;
                this.activeBreakStarts.put(event.getPos(), now);
                if (this.lastBlockBreakCompletionTick >= 0L) {
                    long downtime = Math.max(0L, now - this.lastBlockBreakCompletionTick);
                    this.profile.addBlockBreakDowntime(downtime);
                    this.markDirty();
                }
            }
            case USE -> {
                long now = this.tickCounter;
                if (this.lastBlockPlaceTick >= 0L) {
                    this.profile.addBlockPlaceInterval(Math.max(0L, now - this.lastBlockPlaceTick));
                    this.markDirty();
                }
                this.lastBlockPlaceTick = now;
            }
        }
    }

    @Override
    public synchronized void onBlockChange(BlockChangeEvent event) {
        if (!this.recording || this.activeBreakStarts.isEmpty() || !this.isPlayerActivelyControlling()) {
            return;
        }

        final long now = this.tickCounter;
        for (Pair<BlockPos, BlockState> change : event.getBlocks()) {
            final Long startTick = this.activeBreakStarts.get(change.first());
            if (startTick != null && change.second().isAir()) {
                this.activeBreakStarts.remove(change.first());
                this.profile.addBlockBreakDuration(Math.max(0L, now - startTick));
                this.lastBlockBreakCompletionTick = now;
                this.markDirty();
            }
        }
    }

    @Override
    public synchronized void onWorldEvent(WorldEvent event) {
        if (event.getState() == EventState.POST) {
            this.activeBreakStarts.clear();
            this.lastBlockBreakCompletionTick = -1L;
            this.lastBlockPlaceTick = -1L;
            this.seededMovement = false;
            this.saveProfile();
        }
    }

    private void seedFromPlayer() {
        final LocalPlayer player = this.ctx.player();
        if (player == null || player.input instanceof PlayerMovementInput) {
            this.seededMovement = false;
            return;
        }
        this.lastStrafe = player.xxa;
        this.lastForward = player.zza;
        this.lastYaw = player.getYRot();
        this.lastPitch = player.getXRot();
        this.lastSprinting = player.isSprinting();
        this.lastSprintToggleTick = -1L;
        this.seededMovement = true;
    }

    private boolean isPlayerActivelyControlling() {
        final LocalPlayer player = this.ctx.player();
        return this.recording && player != null && !(player.input instanceof PlayerMovementInput);
    }

    private void markDirty() {
        this.cachedSnapshot = null;
        this.dirty = true;
    }

    private HumanizationProfile loadProfile() {
        if (Files.exists(this.profileFile)) {
            try (Reader reader = Files.newBufferedReader(this.profileFile, StandardCharsets.UTF_8)) {
                HumanizationProfile loaded = this.gson.fromJson(reader, HumanizationProfile.class);
                if (loaded != null) {
                    return loaded;
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new HumanizationProfile();
    }

    private void saveProfile() {
        if (!this.dirty) {
            return;
        }
        try {
            Files.createDirectories(this.profileFile.getParent());
            try (Writer writer = Files.newBufferedWriter(this.profileFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                this.gson.toJson(this.profile, writer);
            }
            this.cachedSnapshot = this.profile.snapshot();
            this.dirty = false;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
