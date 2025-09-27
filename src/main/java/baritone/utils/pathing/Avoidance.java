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

package baritone.utils.pathing;

import baritone.Baritone;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

public class Avoidance {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final double coefficient;
    private final int radius;
    private final int radiusSq;

    public Avoidance(BlockPos center, double coefficient, int radius) {
        this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
    }

    public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.coefficient = coefficient;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public double coefficient(int x, int y, int z) {
        int xDiff = x - centerX;
        int yDiff = y - centerY;
        int zDiff = z - centerZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq ? coefficient : 1.0D;
    }

    public static List<Avoidance> create(IPlayerContext ctx) {
        if (!Baritone.settings().avoidance.value) {
            return Collections.emptyList();
        }
        List<Avoidance> res = new ArrayList<>();
        double mobSpawnerCoeff = Baritone.settings().mobSpawnerAvoidanceCoefficient.value;
        double mobCoeff = Baritone.settings().mobAvoidanceCoefficient.value;
        if (mobSpawnerCoeff != 1.0D) {
            ctx.worldData().getCachedWorld().getLocationsOf("mob_spawner", 1, ctx.playerFeet().x, ctx.playerFeet().z, 2)
                    .forEach(mobspawner -> res.add(new Avoidance(mobspawner, mobSpawnerCoeff, Baritone.settings().mobSpawnerAvoidanceRadius.value)));
        }
        if (mobCoeff != 1.0D) {
            ctx.entitiesStream()
                    .filter(e -> e instanceof Mob)
                    .forEach(e -> {
                        Mob m = (Mob) e;
                        // Neutral logic: enderman only when angry/creepy; piglin only if recently hurt by us
                        if (m instanceof EnderMan && !((EnderMan) m).isCreepy()) return;
                        if (m instanceof ZombifiedPiglin && ((ZombifiedPiglin) m).getLastHurtByMob() == null) return;
                        if (m instanceof Spider && ctx.player().getLightLevelDependentMagicValue() >= 0.5) return; // day spiders chill

                        double coeff = mobCoeff;
                        int radius = Baritone.settings().mobAvoidanceRadius.value;

                        String id = m.getType().toShortString();
                        String simple = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                        boolean isCreeper = simple.contains("creeper");
                        boolean isRanged = simple.contains("skeleton") || simple.contains("stray") || simple.contains("pillager") || simple.contains("blaze") || simple.contains("guardian") || simple.contains("ghast");

                        // LOS-aware boost for ranged mobs
                        if (isRanged && Baritone.settings().combatRangedLOSBoost.value) {
                            try {
                                Vec3 mobEye = new Vec3(m.getX(), m.getY() + m.getEyeHeight(m.getPose()), m.getZ());
                                Vec3 plyEye = new Vec3(ctx.player().getX(), ctx.player().getY() + ctx.player().getEyeHeight(), ctx.player().getZ());
                                var hit = ctx.world().clip(new ClipContext(mobEye, plyEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.player()));
                                boolean clear = hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
                                if (clear) {
                                    radius += Baritone.settings().avoidanceLOSRadiusDelta.value;
                                    coeff = Math.max(coeff, Baritone.settings().avoidanceLOSCoeffMin.value);
                                }
                            } catch (Throwable ignored) {}
                        }

                        if (isCreeper) {
                            // Stronger and larger bubble for creepers
                            radius = Math.max(radius + Baritone.settings().avoidanceCreeperRadiusDelta.value, 7);
                            coeff = Math.max(coeff, Baritone.settings().avoidanceCreeperCoeffMin.value);
                        } else if (isRanged) {
                            radius = Math.max(radius + Baritone.settings().avoidanceRangedRadiusDelta.value, 8);
                            coeff = Math.max(coeff, Baritone.settings().avoidanceRangedCoeffMin.value);
                        } else {
                            radius = Math.max(radius, Baritone.settings().avoidanceMeleeRadiusMin.value);
                            coeff = Math.max(coeff, Baritone.settings().avoidanceMeleeCoeffMin.value);
                        }

                        res.add(new Avoidance(m.blockPosition(), coeff, radius));

                        // Simple future position prediction bubbles (small coefficient)
                        if (Baritone.settings().avoidancePredictionEnabled.value) {
                            var vel = m.getDeltaMovement();
                            if (vel != null) {
                                int[] ticks = new int[]{Baritone.settings().avoidancePredictionTicksShort.value, Baritone.settings().avoidancePredictionTicksLong.value};
                                for (int t : ticks) {
                                    int fx = (int) Math.floor(m.getX() + vel.x * t);
                                    int fy = (int) Math.floor(m.getY() + vel.y * t);
                                    int fz = (int) Math.floor(m.getZ() + vel.z * t);
                                    res.add(new Avoidance(
                                            fx, fy, fz,
                                            Math.min(coeff, Baritone.settings().avoidancePredictionCoeffMin.value),
                                            Math.max(Baritone.settings().avoidancePredictionRadiusMin.value, radius - 2)
                                    ));
                                }
                            }
                        }
                    });
        }
        return res;
    }

    public void applySpherical(Long2DoubleOpenHashMap map) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        long hash = BetterBlockPos.longHash(centerX + x, centerY + y, centerZ + z);
                        map.put(hash, map.get(hash) * coefficient);
                    }
                }
            }
        }
    }
}
